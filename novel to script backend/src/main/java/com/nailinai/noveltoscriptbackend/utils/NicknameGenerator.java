package com.nailinai.noveltoscriptbackend.utils;

import java.util.concurrent.ThreadLocalRandom;

public class NicknameGenerator {

    private static final String[] ADJECTIVES = {
            "快乐", "可爱", "勇敢", "聪明", "调皮", "温柔", "酷酷", "甜甜",
            "阳光", "安静", "活泼", "率真", "潇洒", "热情", "暖心", "软萌"
    };

    private static final String[] NOUNS = {
            "小星", "月亮", "猫咪", "狗狗", "兔子", "小熊", "小鱼", "小鹿",
            "云朵", "彩虹", "糖果", "蛋糕", "奶茶", "西瓜", "草莓", "柠檬"
    };

    public static String generate() {
        String adj = ADJECTIVES[ThreadLocalRandom.current().nextInt(ADJECTIVES.length)];
        String noun = NOUNS[ThreadLocalRandom.current().nextInt(NOUNS.length)];
        int suffix = ThreadLocalRandom.current().nextInt(1000, 9999);
        return adj + noun + suffix;
    }
}
