package cn.garymb.ygomobile.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class DownloadUtilTest {

    @Test
    public void shouldUseChunkedDownloadForSuperpreUrls() {
        assertTrue(DownloadUtil.shouldUseChunkedDownloadForSuperpre("https://cdn02.moecube.com:444/ygopro-super-pre/archive/ygopro-super-pre.ypk"));
        assertTrue(DownloadUtil.shouldUseChunkedDownloadForSuperpre("https://example.com/superpre/file"));
        assertFalse(DownloadUtil.shouldUseChunkedDownloadForSuperpre("https://example.com/normal/file"));
    }

    @Test
    public void buildChunkRangesSplitsContentIntoTenMegabyteRanges() {
        long size = 11L * 1024 * 1024;
        List<DownloadUtil.ChunkRange> ranges = DownloadUtil.buildChunkRanges(size);
        assertEquals(2, ranges.size());
        assertEquals(0L, ranges.get(0).start);
        assertEquals(10L * 1024 * 1024 - 1, ranges.get(0).end);
        assertEquals(10L * 1024 * 1024, ranges.get(1).start);
        assertEquals(size - 1, ranges.get(1).end);
    }
}
