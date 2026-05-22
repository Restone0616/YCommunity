package dev.restone0616.ymp.comment;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

@Getter
public class User {
    private String login;
    private long id;
    @SerializedName("avatar_url")
    private String avatarUrl;
    @SerializedName("html_url")
    private String htmlUrl;
}
