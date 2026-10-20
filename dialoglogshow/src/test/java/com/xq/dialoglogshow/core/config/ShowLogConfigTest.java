package com.xq.dialoglogshow.core.config;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** 文件浏览接入配置的边界测试，确保空白值不会意外启用主 Tab。 */
public final class ShowLogConfigTest {

    @Test
    public void fileBrowserPathIsPreservedAndTitleIsTrimmed() {
        ShowLogConfig config = new ShowLogConfig.Builder()
                .fileBrowserRootPath("  /data/user/0/example/files  ")
                .fileBrowserTabTitle("  调试文件  ")
                .build();

        assertEquals("  /data/user/0/example/files  ", config.fileBrowserRootPath);
        assertEquals("调试文件", config.fileBrowserTabTitle);
    }

    @Test
    public void blankFileBrowserPathDisablesOptionalTab() {
        ShowLogConfig config = new ShowLogConfig.Builder()
                .fileBrowserRootPath("  \t ")
                .fileBrowserTabTitle("  ")
                .build();

        assertNull(config.fileBrowserRootPath);
        assertNull(config.fileBrowserTabTitle);
    }

    @Test
    public void defaultConfigKeepsFileBrowserDisabled() {
        ShowLogConfig config = ShowLogConfig.defaultConfig();

        assertNull(config.fileBrowserRootPath);
        assertNull(config.fileBrowserTabTitle);
    }
}
