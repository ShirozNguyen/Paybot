package com.paybot.milestone;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Biểu diễn một mốc nạp tiền và danh sách các câu lệnh trao thưởng tương ứng.
 * Tuân thủ Quy Tắc 17: Model dữ liệu mốc nạp.
 */
public final class MilestoneModel implements Comparable<MilestoneModel> {

    private final long targetAmount;
    private final List<String> commands;

    public MilestoneModel(long targetAmount, List<String> commands) {
        this.targetAmount = targetAmount;
        this.commands = commands != null ? Collections.unmodifiableList(new ArrayList<>(commands)) : Collections.emptyList();
    }

    public long getTargetAmount() {
        return targetAmount;
    }

    public List<String> getCommands() {
        return commands;
    }

    @Override
    public int compareTo(MilestoneModel other) {
        return Long.compare(this.targetAmount, other.targetAmount);
    }
}
