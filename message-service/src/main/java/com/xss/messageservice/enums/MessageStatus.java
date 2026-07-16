package com.xss.messageservice.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum MessageStatus {
    PENDING(0, "待发送"),
    SUCCESS(1, "发送成功"),
    FAILED(2, "发送失败");

    @EnumValue
    private final int code;
    private final String desc;

    MessageStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}

