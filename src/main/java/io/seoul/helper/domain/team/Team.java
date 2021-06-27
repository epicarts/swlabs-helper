package io.seoul.helper.domain.team;

import io.seoul.helper.domain.member.Member;
import io.seoul.helper.domain.project.Project;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
public class Team {
    @Id
    @Column(name = "team_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //변경할때 안봄, 읽기전용.
    @OneToMany(mappedBy = "team")
    private List<Member> members = new ArrayList<>();

//    @Column(name = "team_start_time")
//    private LocalDateTime startTime;
//
//    @Column(name = "team_end_time")
//    private LocalDateTime endTime;

    @Column(name = "team_max_member_count")
    private Long maxMemberCount;

    @Enumerated(value = EnumType.STRING)
    private TeamLocation location;

    @Enumerated(value = EnumType.STRING)
    private TeamStatus status;

    @ManyToOne
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    public void addMember(Member member) {
        member.setTeam(this);
        members.add(member);
    }
}
