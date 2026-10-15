package com.myk.emotionalHole.util;

import com.myk.emotionalHole.entity.SensitiveWord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 敏感词工具 DFA 检测单元测试
 */
class SensitiveWordUtilsTest {

    private List<SensitiveWord> words;

    @BeforeEach
    void setUp() {
        words = new ArrayList<>();
        addWord("傻逼");
        addWord("滚蛋");
        addWord("自杀");
        addWord("去死吧");
        SensitiveWordUtils.loadSensitiveWordsFromDatabase(words);
    }

    private void addWord(String word) {
        SensitiveWord sw = new SensitiveWord();
        sw.setWord(word);
        words.add(sw);
    }

    @Test
    void shouldLoadWordsAndCount() {
        assertEquals(4, SensitiveWordUtils.getSensitiveWordCount());
    }

    @Test
    void shouldDetectSensitiveWordAtBeginning() {
        assertTrue(SensitiveWordUtils.containsSensitiveWord("傻逼闭嘴"));
    }

    @Test
    void shouldDetectSensitiveWordInMiddle() {
        assertTrue(SensitiveWordUtils.containsSensitiveWord("你真是个傻逼吧"));
    }

    @Test
    void shouldDetectSensitiveWordAtEnd() {
        assertTrue(SensitiveWordUtils.containsSensitiveWord("快给我滚蛋"));
    }

    @Test
    void shouldDetectMultiCharWordWithSharedPrefix() {
        assertTrue(SensitiveWordUtils.containsSensitiveWord("不想活了想去死吧"));
    }

    @Test
    void shouldNotDetectNormalText() {
        assertFalse(SensitiveWordUtils.containsSensitiveWord("今天天气真好，心情不错"));
    }

    @Test
    void shouldHandleEmptyAndNullText() {
        assertFalse(SensitiveWordUtils.containsSensitiveWord(""));
        assertFalse(SensitiveWordUtils.containsSensitiveWord(null));
    }

    @Test
    void shouldIgnoreEmptyWordWhenLoading() {
        SensitiveWord empty = new SensitiveWord();
        empty.setWord("  ");
        words.add(empty);
        SensitiveWordUtils.loadSensitiveWordsFromDatabase(words);
        assertEquals(4, SensitiveWordUtils.getSensitiveWordCount());
    }
}
