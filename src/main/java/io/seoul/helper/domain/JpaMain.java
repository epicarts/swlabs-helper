package io.seoul.helper.domain;

import io.seoul.helper.domain.member.Member;
import io.seoul.helper.domain.order.Order;
import io.seoul.helper.domain.project.Project;
import io.seoul.helper.domain.team.Team;
import io.seoul.helper.domain.team.TeamLocation;
import io.seoul.helper.domain.team.TeamStatus;
import io.seoul.helper.domain.user.Role;
import io.seoul.helper.domain.user.User;
import sun.jvm.hotspot.gc_implementation.parallelScavenge.PSYoungGen;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;
import javax.swing.text.html.parser.Entity;
import java.net.UnknownServiceException;
import java.util.List;

public class JpaMain {
    public static void main(String[] args) {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("hello");
        EntityManager em = emf.createEntityManager();
        EntityTransaction tx = em.getTransaction(); // 트랜잭션 시작.

        tx.begin();
        try {

            Project project = new Project();
            project.setName("ft_libt");
            em.persist(project); // 영속 상태가 됨.

            User user = User.builder()
                    .fullname("Younghox")
                    .nickname("ychois")
                    .picture("https://cdn.intra.42.fr/users/ychoi.jpg")
                    .role(Role.USER)
                    .email("ychoi@naver.com")
                    .build();
            em.persist(user); // user에 insert 쿼리를 날림 그래야 id값을 가져올 수 있음.

            Team team = new Team();
            team.setMaxMemberCount((long) 3);
            team.setLocation(TeamLocation.GAEPO);
            team.setStatus(TeamStatus.END);
            team.setProject(project);
            em.persist(team);



            Member member = new Member();
            member.setTeam(team); //연관관계의 주인에 설정. 이게 맞음.
            member.setUser(user);

            team.getMembers().add(member); // 팀에서 추가. 여기에도 1차 캐시를 위해 추가. 그치만 둘다 ㄴㄴ 차라리 메소드를 만들자.

            em.persist(member); // em.find(Team.class, team.getId());

            team.addMember(member); //


            em.flush(); // 디비에 반영.
            em.clear(); // 영속성 컨텍스트 지

            Team findTeam = em.find(Team.class, team.getId());
            List<Member> members = findTeam.getMembers();


            System.out.println("ㅁ둔ㅇ리ㅏㅜㄴ이ㅏㅁ민위ㅏㅁ누이ㅏ문이ㅏㅜㅁㄴㅇ ");
//
//            team.getMembers().add(member);
//
//            Member member = new Member();
//            member.setTeam(team);
//            Team team = new Team();
//            em.persist(team);
            tx.commit();
        } catch (Exception e) {
            tx.rollback();
            em.close();
        }
        emf.close();
    }
}
