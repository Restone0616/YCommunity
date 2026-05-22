package dev.restone0616.ymp.comment;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

@Getter
public class CommentItem {
    private long id;
    private String body;
    @SerializedName("created_at")
    private String createdAt;
    @SerializedName("updated_at")
    private String updatedAt;
    @SerializedName("html_url")
    private String htmlUrl;
    private User user;
}
