package com.nbn.adfeed.ui.feed;

/**
 * 列表底部 footer 的状态。
 *
 * <ul>
 *   <li>{@link #HIDDEN}：不展示 footer（例如空态时）。</li>
 *   <li>{@link #LOADING}：正在加载下一页。</li>
 *   <li>{@link #NO_MORE}：已经没有更多数据。</li>
 *   <li>{@link #ERROR}：加载下一页失败，点击 footer 可重试。</li>
 * </ul>
 */
public enum FooterState {
    HIDDEN,
    LOADING,
    NO_MORE,
    ERROR
}