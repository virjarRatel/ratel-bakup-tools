package com.virjar.ratel.backup.v2.devices;

import lombok.Getter;

/**
 * 任务状态
 */
public enum TaskStatus {
    INIT(false),
    BACKUP(true),
    STORE(true),
    SUCCESS(false),
    FAILED(false);
    @Getter
    private boolean doing;

    TaskStatus(boolean doing) {
        this.doing = doing;
    }
}
