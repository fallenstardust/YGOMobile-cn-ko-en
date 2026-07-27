package cn.garymb.ygomobile.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class DownloadUtilTest {

    @Test
    public void shouldUseChunkedDownloadForSuperpreUrls() {
        assertTrue(DownloadUtil.shouldUseChunkedDownload("https://cdn02.moecube.com:444/ygopro-super-pre/archive/ygopro-super-pre.ypk"));
        assertTrue(DownloadUtil.shouldUseChunkedDownload("https://example.com/superpre/file"));
        assertFalse(DownloadUtil.shouldUseChunkedDownload("https://example.com/normal/file"));
    }

    @Test
    public void buildChunkRangesSplitsContentIntoMultipleRanges() {
        List<DownloadUtil.ChunkRange> ranges = DownloadUtil.buildChunkRanges(10L, 4);
        assertEquals(4, ranges.size());
        assertEquals(0L, ranges.get(0).start);
        assertEquals(2L, ranges.get(0).end);
        assertEquals(3L, ranges.get(1).start);
        assertEquals(5L, ranges.get(1).end);
        assertEquals(6L, ranges.get(2).start);
        assertEquals(8L, ranges.get(2).end);
        assertEquals(9L, ranges.get(3).start);
        assertEquals(9L, ranges.get(3).end);
    }
}
