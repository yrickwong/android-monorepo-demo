package com.demo.foundations.slidepane

/**
 * 标记框架对外公开的 API。
 *
 * 几亿 DAU 场景下，标注此注解的符号变更需遵循以下流程：
 * 1. 双月通告 + 灰度
 * 2. 通过 metalava / binary-compatibility-validator CI 守护
 * 3. 新增方法须提供 default 实现，保证向后兼容
 */
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FIELD,
    AnnotationTarget.TYPEALIAS
)
annotation class PaneApi
