package com.myk.emotionalHole.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.myk.emotionalHole.entity.SensitiveWord;
import com.myk.emotionalHole.mapper.SensitiveWordMapper;
import com.myk.emotionalHole.service.SensitiveWordService;
import com.myk.emotionalHole.util.SensitiveWordUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 敏感词服务实现类
 *
 * 提供敏感词CRUD、DFA算法加载、批量导入功能
 * 应用启动时自动从数据库加载到内存Trie树
 */
@Service
public class SensitiveWordServiceImpl implements SensitiveWordService {

    private static final Logger logger = LoggerFactory.getLogger(SensitiveWordServiceImpl.class);

    @Resource
    private SensitiveWordMapper sensitiveWordMapper;

    /**
     * 应用启动时自动加载敏感词
     */
    @PostConstruct
    public void init() {
        loadSensitiveWords();
    }

    /** 从数据库加载敏感词到内存DFA词库 */
    @Override
    public void loadSensitiveWords() {
        try {
            List<SensitiveWord> sensitiveWordList = sensitiveWordMapper.selectList(
                    Wrappers.<SensitiveWord>lambdaQuery().eq(SensitiveWord::getKeywordType, 1));
            SensitiveWordUtils.loadSensitiveWordsFromDatabase(sensitiveWordList);
            logger.info("敏感词加载成功，共加载 {} 个普通敏感词", SensitiveWordUtils.getSensitiveWordCount());
        } catch (Exception e) {
            logger.error("从数据库加载敏感词失败，使用默认敏感词: {}", e.getMessage(), e);
        }
    }

    @Override
    public List<SensitiveWord> getAllSensitiveWords() {
        return sensitiveWordMapper.selectList(Wrappers.<SensitiveWord>lambdaQuery()
                .orderByAsc(SensitiveWord::getKeywordType)
                .orderByAsc(SensitiveWord::getId));
    }

    /** 添加敏感词并重新加载词库 */
    @Override
    public int addSensitiveWord(SensitiveWord sensitiveWord) {
        try {
            // 设置创建时间
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            sensitiveWord.setCreateTime(LocalDateTime.now().format(formatter));

            int result = sensitiveWordMapper.insert(sensitiveWord);
            if (result > 0) {
                // 重新加载敏感词
                loadSensitiveWords();
                logger.info("添加敏感词成功: {}", sensitiveWord.getWord());
            }
            return result;
        } catch (Exception e) {
            logger.error("添加敏感词失败: {}", e.getMessage(), e);
            return 0;
        }
    }

    /** 更新敏感词并重新加载词库 */
    @Override
    public int updateSensitiveWord(SensitiveWord sensitiveWord) {
        try {
            int result = sensitiveWordMapper.updateById(sensitiveWord);
            if (result > 0) {
                // 重新加载敏感词
                loadSensitiveWords();
                logger.info("更新敏感词成功: {}", sensitiveWord.getWord());
            }
            return result;
        } catch (Exception e) {
            logger.error("更新敏感词失败: {}", e.getMessage(), e);
            return 0;
        }
    }

    /** 删除敏感词并重新加载词库 */
    @Override
    public int deleteSensitiveWord(Long id) {
        try {
            // 先获取敏感词内容用于日志记录
            SensitiveWord word = sensitiveWordMapper.selectById(id);
            String wordContent = word != null ? word.getWord() : "未知敏感词";

            int result = sensitiveWordMapper.deleteById(id);
            if (result > 0) {
                // 重新加载敏感词
                loadSensitiveWords();
                logger.info("删除敏感词成功: {}", wordContent);
            }
            return result;
        } catch (Exception e) {
            logger.error("删除敏感词失败: {}", e.getMessage(), e);
            return 0;
        }
    }


    @Override
    public SensitiveWord getSensitiveWordByWordAndType(String word, int keywordType) {
        return sensitiveWordMapper.selectOne(Wrappers.<SensitiveWord>lambdaQuery()
                .eq(SensitiveWord::getWord, word)
                .eq(SensitiveWord::getKeywordType, keywordType));
    }

    @Override
    public List<SensitiveWord> getSensitiveWordsByType(int keywordType) {
        return sensitiveWordMapper.selectList(Wrappers.<SensitiveWord>lambdaQuery()
                .eq(SensitiveWord::getKeywordType, keywordType)
                .orderByAsc(SensitiveWord::getId));
    }

    @Override
    public List<SensitiveWord> searchSensitiveWordsByKeywordAndType(String keyword, int keywordType, int page, int pageSize) {
        Page<SensitiveWord> pageResult = sensitiveWordMapper.selectPage(new Page<>(page, pageSize),
                Wrappers.<SensitiveWord>lambdaQuery()
                        .like(SensitiveWord::getWord, keyword)
                        .eq(SensitiveWord::getKeywordType, keywordType)
                        .orderByDesc(SensitiveWord::getId));
        return pageResult.getRecords();
    }

    @Override
    public int countSensitiveWordsByKeywordAndType(String keyword, int keywordType) {
        return Math.toIntExact(sensitiveWordMapper.selectCount(Wrappers.<SensitiveWord>lambdaQuery()
                .like(SensitiveWord::getWord, keyword)
                .eq(SensitiveWord::getKeywordType, keywordType)));
    }

    @Override
    public List<SensitiveWord> getCrisisWordsByPage(int page, int pageSize) {
        Page<SensitiveWord> pageResult = sensitiveWordMapper.selectPage(new Page<>(page, pageSize),
                Wrappers.<SensitiveWord>lambdaQuery()
                        .in(SensitiveWord::getKeywordType, 2, 3)
                        .orderByDesc(SensitiveWord::getId));
        return pageResult.getRecords();
    }

    @Override
    public int countCrisisWords() {
        return Math.toIntExact(sensitiveWordMapper.selectCount(Wrappers.<SensitiveWord>lambdaQuery()
                .in(SensitiveWord::getKeywordType, 2, 3)));
    }

    @Override
    public List<SensitiveWord> searchCrisisWordsByKeyword(String keyword, int page, int pageSize) {
        Page<SensitiveWord> pageResult = sensitiveWordMapper.selectPage(new Page<>(page, pageSize),
                Wrappers.<SensitiveWord>lambdaQuery()
                        .in(SensitiveWord::getKeywordType, 2, 3)
                        .like(SensitiveWord::getWord, keyword)
                        .orderByDesc(SensitiveWord::getId));
        return pageResult.getRecords();
    }

    @Override
    public int countCrisisWordsByKeyword(String keyword) {
        return Math.toIntExact(sensitiveWordMapper.selectCount(Wrappers.<SensitiveWord>lambdaQuery()
                .in(SensitiveWord::getKeywordType, 2, 3)
                .like(SensitiveWord::getWord, keyword)));
    }

    /** 批量导入敏感词并重新加载词库 */
    @Override
    public int batchImportSensitiveWords(List<SensitiveWord> sensitiveWords) {
        try {
            // 设置创建时间
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String currentTime = LocalDateTime.now().format(formatter);

            for (SensitiveWord word : sensitiveWords) {
                word.setCreateTime(currentTime);
            }

            int result = sensitiveWordMapper.batchInsertSensitiveWords(sensitiveWords);
            if (result > 0) {
                // 重新加载敏感词
                loadSensitiveWords();
                logger.info("批量导入敏感词成功，共导入 {} 个敏感词", result);
            }
            return result;
        } catch (Exception e) {
            logger.error("批量导入敏感词失败: {}", e.getMessage(), e);
            return 0;
        }
    }

    @Override
    public List<SensitiveWord> getSensitiveWordsByPage(int page, int pageSize) {
        try {
            Page<SensitiveWord> pageResult = sensitiveWordMapper.selectPage(new Page<>(page, pageSize),
                    Wrappers.<SensitiveWord>lambdaQuery().orderByDesc(SensitiveWord::getId));
            return pageResult.getRecords();
        } catch (Exception e) {
            logger.error("分页获取敏感词失败: {}", e.getMessage(), e);
            return java.util.Collections.emptyList();
        }
    }

    @Override
    public List<SensitiveWord> getSensitiveWordsByTypeAndPage(Integer keywordType, int page, int pageSize) {
        try {
            LambdaQueryWrapper<SensitiveWord> wrapper = Wrappers.lambdaQuery();
            if (keywordType != null) {
                wrapper.eq(SensitiveWord::getKeywordType, keywordType);
            }
            wrapper.orderByDesc(SensitiveWord::getId);
            Page<SensitiveWord> pageResult = sensitiveWordMapper.selectPage(new Page<>(page, pageSize), wrapper);
            return pageResult.getRecords();
        } catch (Exception e) {
            logger.error("根据类型分页获取敏感词失败: {}", e.getMessage(), e);
            return java.util.Collections.emptyList();
        }
    }

    @Override
    public int getSensitiveWordCount() {
        try {
            return Math.toIntExact(sensitiveWordMapper.selectCount(null));
        } catch (Exception e) {
            logger.error("获取敏感词总数失败: {}", e.getMessage(), e);
            return 0;
        }
    }

    @Override
    public int getSensitiveWordCountByType(Integer keywordType) {
        try {
            LambdaQueryWrapper<SensitiveWord> wrapper = Wrappers.lambdaQuery();
            if (keywordType != null) {
                wrapper.eq(SensitiveWord::getKeywordType, keywordType);
            }
            return Math.toIntExact(sensitiveWordMapper.selectCount(wrapper));
        } catch (Exception e) {
            logger.error("根据类型获取敏感词总数失败: {}", e.getMessage(), e);
            return 0;
        }
    }

    @Override
    public List<SensitiveWord> searchSensitiveWordsByKeyword(String keyword, int page, int pageSize) {
        try {
            Page<SensitiveWord> pageResult = sensitiveWordMapper.selectPage(new Page<>(page, pageSize),
                    Wrappers.<SensitiveWord>lambdaQuery()
                            .like(SensitiveWord::getWord, keyword)
                            .orderByDesc(SensitiveWord::getId));
            return pageResult.getRecords();
        } catch (Exception e) {
            logger.error("根据关键词搜索敏感词失败: {}", e.getMessage(), e);
            return java.util.Collections.emptyList();
        }
    }

    @Override
    public int countSensitiveWordsByKeyword(String keyword) {
        try {
            return Math.toIntExact(sensitiveWordMapper.selectCount(Wrappers.<SensitiveWord>lambdaQuery()
                    .like(SensitiveWord::getWord, keyword)));
        } catch (Exception e) {
            logger.error("根据关键词统计敏感词数量失败: {}", e.getMessage(), e);
            return 0;
        }
    }
}
