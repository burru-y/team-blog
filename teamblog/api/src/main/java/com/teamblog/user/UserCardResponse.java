package com.teamblog.user;

import java.util.UUID;

/**
 * 成员列表里的一张卡片。
 *
 * <p>为什么不直接复用 {@link UserResponse}：列表页只需要展示用得到的几个字段。
 * 把邮箱、创建时间这些"详情才有意义"的字段塞进列表，等于给每个响应多背一份数据，
 * 也多一份泄漏面（S-10 的思路：按用途裁剪，而不是给全量）。
 */
public record UserCardResponse(
        UUID id,
        String username,
        /** 显示名。为空时等于 username，前端不必再判空。 */
        String displayName,
        String role,
        String avatarUrl,
        String bio,
        /** 名片强调色，首页成员卡用它给跟随光标的光晕上色。 */
        String accentColor,
        int totalPoints
) {

    public static UserCardResponse from(User user) {
        return new UserCardResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName() == null || user.getDisplayName().isBlank()
                        ? user.getUsername()
                        : user.getDisplayName(),
                user.getRole().name(),
                user.getAvatarUrl(),
                user.getBio(),
                user.getAccentColor(),
                user.getTotalPoints());
    }
}
