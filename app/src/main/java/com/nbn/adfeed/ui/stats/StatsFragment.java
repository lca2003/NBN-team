package com.nbn.adfeed.ui.stats;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.nbn.adfeed.R;
import com.nbn.adfeed.data.model.AdContentType;
import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.repository.AdRepository;
import com.nbn.adfeed.ui.feed.InteractionStore;

import java.util.List;

/**
 * 统计数据展示页。
 *
 * <p>页面从共享的 {@link AdRepository} 获取广告元数据，并从 {@link InteractionStore}
 * 读取本地曝光、点击、点赞/收藏状态。这样 Feed 中产生的互动结果可以即时反映到统计图表。</p>
 */
public final class StatsFragment extends Fragment {
    private final InteractionStore interactionStore = InteractionStore.get();
    private AdRepository adRepository;
    private TextView totalExposureValue;
    private TextView totalClickValue;
    private TextView clickRateValue;
    private LinearLayout topAdsContainer;
    private PieChartView contentTypePieChart;
    private TextView contentTypeTotal;
    private TextView largeImageLegend;
    private TextView smallImageLegend;
    private TextView videoLegend;
    private LinearLayout tagHeatContainer;

    /** 由宿主 Activity 注入共享广告数据源，保证统计页与信息流使用同一批广告。 */
    public void configure(AdRepository repository) {
        adRepository = repository;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_stats, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        totalExposureValue = view.findViewById(R.id.totalExposureValue);
        totalClickValue = view.findViewById(R.id.totalClickValue);
        clickRateValue = view.findViewById(R.id.clickRateValue);
        topAdsContainer = view.findViewById(R.id.topAdsContainer);
        contentTypePieChart = view.findViewById(R.id.contentTypePieChart);
        contentTypeTotal = view.findViewById(R.id.contentTypeTotal);
        largeImageLegend = view.findViewById(R.id.largeImageLegend);
        smallImageLegend = view.findViewById(R.id.smallImageLegend);
        videoLegend = view.findViewById(R.id.videoLegend);
        tagHeatContainer = view.findViewById(R.id.tagHeatContainer);
        render();
    }

    /** 返回统计页时重新渲染，补齐从 Feed/详情页返回后产生的新曝光或点击。 */
    @Override
    public void onResume() {
        super.onResume();
        if (getView() != null) {
            render();
        }
    }

    /** 汇总当前广告和互动状态，并分别刷新各个统计模块。 */
    private void render() {
        Context context = getContext();
        if (adRepository == null || context == null) {
            return;
        }

        List<AdItem> ads = adRepository.getInitialAds();
        StatsSummary summary = StatsSummaryBuilder.fromAds(ads, interactionStore);
        renderMetrics(summary);
        renderTopAds(summary);
        renderContentTypes(summary);
        renderTags(summary);
    }

    /** 顶部三张指标卡：总曝光、总点击和点击率。 */
    private void renderMetrics(StatsSummary summary) {
        totalExposureValue.setText(String.valueOf(summary.getTotalExposureCount()));
        totalClickValue.setText(String.valueOf(summary.getTotalClickCount()));
        clickRateValue.setText(getString(R.string.stats_percent_format, summary.getClickRatePercent()));
    }

    /** 热门广告模块：只有产生过曝光时才展示排行，否则展示空态提示。 */
    private void renderTopAds(StatsSummary summary) {
        topAdsContainer.removeAllViews();
        List<StatsSummary.TopAd> topAds = summary.getTopAds();
        int maxExposure = 0;
        for (StatsSummary.TopAd topAd : topAds) {
            maxExposure = Math.max(maxExposure, topAd.getExposureCount());
        }

        if (topAds.isEmpty() || maxExposure <= 0) {
            TextView emptyView = createText(14, R.color.nbn_text_secondary, Typeface.NORMAL);
            emptyView.setText(R.string.stats_empty_top_ads);
            topAdsContainer.addView(emptyView);
            return;
        }

        for (StatsSummary.TopAd topAd : topAds) {
            topAdsContainer.addView(createTopAdRow(topAd, maxExposure));
        }
    }

    /** 创建单条热门广告行，包含标题、曝光/点击文本和一条曝光占比条。 */
    private View createTopAdRow(StatsSummary.TopAd topAd, int maxExposure) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rowParams.bottomMargin = dpToPx(12);
        row.setLayoutParams(rowParams);

        TextView titleView = createText(15, R.color.nbn_text_primary, Typeface.BOLD);
        titleView.setText(topAd.getTitle());
        row.addView(titleView);

        TextView metricView = createText(13, R.color.nbn_text_secondary, Typeface.NORMAL);
        metricView.setText(getString(
                R.string.stats_top_ad_line_format,
                topAd.getExposureCount(),
                topAd.getClickCount()
        ));
        LinearLayout.LayoutParams metricParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        metricParams.topMargin = dpToPx(4);
        metricView.setLayoutParams(metricParams);
        row.addView(metricView);

        row.addView(createExposureBar(topAd.getExposureCount(), maxExposure));
        return row;
    }

    /**
     * 用横向权重绘制曝光条，避免固定像素宽度在小屏设备上溢出。
     *
     * <p>bar 的权重为当前曝光数，spacer 的权重为剩余曝光差值。</p>
     */
    private View createExposureBar(int exposureCount, int maxExposure) {
        LinearLayout track = new LinearLayout(requireContext());
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setBackgroundColor(requireContext().getColor(R.color.nbn_divider));
        LinearLayout.LayoutParams trackParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(8)
        );
        trackParams.topMargin = dpToPx(8);
        track.setLayoutParams(trackParams);

        View bar = new View(requireContext());
        bar.setBackgroundColor(requireContext().getColor(R.color.nbn_brand));
        track.addView(bar, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, exposureCount));

        int remaining = Math.max(0, maxExposure - exposureCount);
        View spacer = new View(requireContext());
        track.addView(spacer, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, remaining));
        return track;
    }

    /** 内容类型模块：饼状图展示大图/小图/视频占比，右侧 legend 展示数量和百分比。 */
    private void renderContentTypes(StatsSummary summary) {
        int largeCount = summary.getContentTypeCount(AdContentType.LARGE_IMAGE);
        int smallCount = summary.getContentTypeCount(AdContentType.SMALL_IMAGE);
        int videoCount = summary.getContentTypeCount(AdContentType.VIDEO);
        int largeColor = requireContext().getColor(R.color.nbn_chart_large_image);
        int smallColor = requireContext().getColor(R.color.nbn_chart_small_image);
        int videoColor = requireContext().getColor(R.color.nbn_chart_video);

        contentTypePieChart.setSlices(
                new int[]{largeCount, smallCount, videoCount},
                new int[]{largeColor, smallColor, videoColor}
        );
        contentTypeTotal.setText(getString(R.string.stats_total_ads_format, summary.getTotalAdCount()));
        largeImageLegend.setText(getLegendLine(R.string.stats_type_large_image, largeCount,
                summary.getContentTypePercent(AdContentType.LARGE_IMAGE)));
        smallImageLegend.setText(getLegendLine(R.string.stats_type_small_image, smallCount,
                summary.getContentTypePercent(AdContentType.SMALL_IMAGE)));
        videoLegend.setText(getLegendLine(R.string.stats_type_video, videoCount,
                summary.getContentTypePercent(AdContentType.VIDEO)));
    }

    /** 标签热度模块：每三枚 chip 自动换一行，避免窄屏横向挤压。 */
    private void renderTags(StatsSummary summary) {
        tagHeatContainer.removeAllViews();
        LinearLayout row = null;
        int index = 0;
        for (StatsSummary.TagHeat tagHeat : summary.getTagHeat()) {
            if (index % 3 == 0) {
                row = createTagRow();
                tagHeatContainer.addView(row);
            }

            TextView chip = createText(13, R.color.nbn_text_primary, Typeface.NORMAL);
            chip.setGravity(Gravity.CENTER);
            chip.setBackgroundResource(R.drawable.bg_tag_chip);
            chip.setText(tagHeat.getName() + " " + tagHeat.getCount());
            chip.setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6));

            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1
            );
            chipParams.setMargins(dpToPx(3), dpToPx(3), dpToPx(3), dpToPx(3));
            row.addView(chip, chipParams);
            index++;
        }
    }

    /** 创建一行标签 chip 容器，StatsFragment 每三枚标签另起一行。 */
    private LinearLayout createTagRow() {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return row;
    }

    /** 创建统一样式的文本控件，供动态列表和 chip 复用。 */
    private TextView createText(int textSizeSp, int colorResId, int typefaceStyle) {
        TextView textView = new TextView(requireContext());
        textView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        textView.setTextColor(requireContext().getColor(colorResId));
        textView.setTextSize(textSizeSp);
        textView.setTypeface(Typeface.DEFAULT, typefaceStyle);
        return textView;
    }

    /** 拼接右侧图例文案，例如“大图 9 条    34%”。 */
    private String getLegendLine(int typeResId, int count, int percent) {
        String typeLine = getString(R.string.stats_type_line_format, getString(typeResId), count);
        return typeLine + "    " + getString(R.string.stats_percent_format, percent);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
