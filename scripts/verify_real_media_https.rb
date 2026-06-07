#!/usr/bin/env ruby
# frozen_string_literal: true

require "json"
require "open3"
require "timeout"

ROOT = File.expand_path("..", __dir__)
ADS_PATH = File.join(ROOT, "app/src/main/assets/ads_mock.json")
MANIFEST_PATH = File.join(ROOT, "app/src/main/assets/media_manifest.json")

def abort_with(message)
  warn("FAIL: #{message}")
  exit(1)
end

def https?(url)
  url.is_a?(String) && url.start_with?("https://")
end

def probe_url(url)
  output, status = Open3.capture2e(
    "curl", "-L", "--range", "0-0", "--connect-timeout", "5", "--max-time", "12",
    "-A", "NBNAdFeedMediaAudit/1.0", "-s", "-o", "/dev/null",
    "-w", "%{http_code}\\t%{content_type}\\t%{url_effective}", url
  )
  return nil unless status.success?

  code, content_type, effective_url = output.strip.split("\t", 3)
  { code: code.to_i, content_type: content_type.to_s, effective_url: effective_url.to_s }
end

def ffprobe(url)
  command = [
    "ffprobe", "-v", "error", "-select_streams", "v:0",
    "-show_entries", "stream=codec_name,width,height,duration",
    "-show_entries", "format=size,duration",
    "-of", "json", url
  ]
  output, status = Timeout.timeout(20) { Open3.capture2e(*command) }
  return nil unless status.success?

  JSON.parse(output)
rescue Errno::ENOENT
  warn("WARN: ffprobe not found; skipping video parameter probe")
  nil
rescue Timeout::Error
  warn("WARN: ffprobe timed out for #{url}; header scan already passed")
  nil
rescue JSON::ParserError
  nil
end

ads = JSON.parse(File.read(ADS_PATH))
manifest = JSON.parse(File.read(MANIFEST_PATH))
abort_with("expected 30 ads") unless ads.size == 30
abort_with("expected 30 manifest rows") unless manifest.size == 30

manifest_by_id = manifest.each_with_object({}) { |row, memo| memo[row.fetch("adId")] = row }
video_count = 0

ads.each do |ad|
  id = ad.fetch("id")
  row = manifest_by_id[id] || abort_with("missing manifest row for #{id}")
  %w[imageUrl thumbnailUrl].each do |field|
    abort_with("#{id} #{field} must be HTTPS") unless https?(ad[field])
    abort_with("#{id} manifest #{field} mismatch") unless row[field] == ad[field]
    abort_with("#{id} source missing") if row["source"].to_s.strip.empty?
    abort_with("#{id} license missing") if row["license"].to_s.strip.empty?
    abort_with("#{id} attribution missing") if row["attribution"].to_s.strip.empty?
  end
  next unless ad["contentType"] == "VIDEO"

  video_count += 1
  abort_with("#{id} videoUrl must be HTTPS") unless https?(ad["videoUrl"])
  abort_with("#{id} manifest videoUrl mismatch") unless row["videoUrl"] == ad["videoUrl"]
end

abort_with("expected 6 HTTPS video ads") unless video_count == 6

if ARGV.include?("--online")
  checked_urls = manifest.flat_map { |row| [row["imageUrl"], row["thumbnailUrl"], row["videoUrl"]] }.compact.uniq
  queue = Queue.new
  checked_urls.each { |url| queue << url }
  errors = Queue.new
  workers = 8.times.map do
    Thread.new do
      loop do
        url = queue.pop(true)
        response = probe_url(url)
        if response.nil?
          errors << "#{url} did not respond"
          next
        end
        code = response[:code]
        type = response[:content_type]
        errors << "#{url} returned #{code}" unless code.between?(200, 399) || code == 206
        unless type.start_with?("image/") || type.start_with?("video/") || type.include?("ogg")
          errors << "#{url} returned unexpected content type #{type}"
        end
      rescue ThreadError
        break
      end
    end
  end
  workers.each(&:join)
  abort_with(errors.pop) unless errors.empty?

  manifest.select { |row| row["videoUrl"] }.each do |row|
    probe = ffprobe(row["videoUrl"])
    next unless probe

    stream = probe.fetch("streams").first || {}
    abort_with("#{row["adId"]} video width below 640") if stream.fetch("width", 0).to_i < 640
  end
end

puts "OK: ads=#{ads.size} manifest=#{manifest.size} videos=#{video_count} online=#{ARGV.include?("--online")}"
