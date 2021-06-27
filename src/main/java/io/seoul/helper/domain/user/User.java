package io.seoul.helper.domain.user;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;

@Entity
@NoArgsConstructor
@Getter
@Setter
public class User {
    @Id
    @Column(name = "user_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nickname;

    @Column(nullable = false)
    private String fullname;

    @Column(nullable = false)
    private String email;
   
    @Column
    private String picture;

    @Enumerated(value = EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Builder
    public User(String nickname, String fullname, String email, String picture, Role role) {
        this.nickname = nickname;
        this.fullname = fullname;
        this.email = email;
        this.picture = picture;
        this.role = role;
    }

    public User update(String nickname, String fullname, String picture) {
        this.nickname = nickname;
        this.fullname = fullname;
        this.picture = picture;
        return this;
    }

    public String getRoleKey() {
        return this.role.getKey();
    }
}
