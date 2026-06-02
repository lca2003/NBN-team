package com.nbn.adfeed.ui.search;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public final class ChatMessageIdsCodecTest {
    @Test
    public void encodesAndDecodesMatchedAdIds() {
        String encoded = ChatMessageIdsCodec.encode(Arrays.asList("ad_001", "ad_002"));

        assertEquals("[\"ad_001\",\"ad_002\"]", encoded);
        assertEquals(Arrays.asList("ad_001", "ad_002"), ChatMessageIdsCodec.decode(encoded));
    }

    @Test
    public void emptyAndInvalidValuesDecodeToEmptyList() {
        assertEquals(Collections.emptyList(), ChatMessageIdsCodec.decode(null));
        assertEquals(Collections.emptyList(), ChatMessageIdsCodec.decode(""));
        assertEquals(Collections.emptyList(), ChatMessageIdsCodec.decode("not json"));
    }
}
