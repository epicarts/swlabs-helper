package io.seoul.helper.domain.member;

import io.seoul.helper.domain.team.Team;
import io.seoul.helper.domain.user.User;
import jdk.nashorn.internal.objects.annotations.Getter;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Setter
public class Member {
    @Id
    @Column(name = "member_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "member_team_id", nullable = false)
    private Team team;

    @ManyToOne
    @JoinColumn(name = "member_user_id", nullable = false)
    private User user;
}
