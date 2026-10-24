package com.teamblog.learning;

/**
 * 学习内容的类型，对应 {@code learning_logs.type} 的 CHECK。
 *
 * <p>{@code NOTE} = 手写笔记；{@code CODE} = 上传代码文件后转成的 Markdown 代码块
 * （批次 3.3）。两者都计分，区别只在于展示方式。
 */
public enum LearningLogType {

    NOTE,
    CODE
}
