package com.zhoufz.tools.service;

/**
 * 移植范围：联机、前端、低柜，可独立开关。
 *
 * @author zhoufz
 * @date 20260705
 */
public enum TransplantScope {

    /** 联机（lcpt-server） */
    ONLINE,

    /** 前端（lcpt-front，仅个性化 vue / 路由） */
    FRONT,

    /** 低柜（ifmcounter） */
    COUNTER
}
