package study.querydsl;

import static com.querydsl.jpa.JPAExpressions.*;
import static org.assertj.core.api.Assertions.*;
import static study.querydsl.entity.QMember.*;
import static study.querydsl.entity.QTeam.*;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;
import jakarta.transaction.Transactional;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import study.querydsl.dto.MemberDto;
import study.querydsl.dto.QMemberDto;
import study.querydsl.dto.UserDto;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.QTeam;
import study.querydsl.entity.Team;

@SpringBootTest
@Transactional
public class QuerydslBasicTest {

    @Autowired
    EntityManager em;

    JPAQueryFactory queryFactory; // 필드로 빼는걸 권장. EntityManager 자체가 멀티스레드 동시성 문제 없도록 설계되어 있음.

    @BeforeEach //데이터 미리 세팅
    public void before() {
        queryFactory = new JPAQueryFactory(em);
        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");
        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);
        Member member3 = new Member("member3", 10, teamB);
        Member member4 = new Member("member4", 20, teamB);
        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);
    }

    @Test
    public void startJPQL() {
        //member1을 찾아라
        String qlString =
            "select m from Member m " +
            "where m.username = :username"; // 쿼리스트링으로 쿼리 분리, 커맨드 + 옵션 + v

        Member findMember = em.createQuery(qlString, Member.class)
            .setParameter("username", "member1")
            .getSingleResult();

        assertThat(findMember.getUsername()).isEqualTo("member1");

    }

    @Test
    public void startQuerydsl() {
        // QMember m = new QMember("m"); // 어떤 QMember인지 구분하는 'm' 파라미터 - 별칭(alias) 방식
        // QMember m = QMember.member;

        Member findMember = queryFactory
            .select(member) // static import 방식으로 QMember 가져오기 (Option + Enter) 방식 권장
            .from(member)
            .where(member.username.eq("member1")) // 파라미터 바인딩 자동
            .fetchOne(); // 단건 조회

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void search() {
        Member findMember = queryFactory
            .selectFrom(member)
            .where(member.username.eq("member1")
                .and(member.age.eq(10)))
            .fetchOne(); // 단건 조회

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void searchAndParam() {
        Member findMember = queryFactory
            .selectFrom(member)
            .where(
                member.username.eq("member1"),
                member.age.eq(10) // and만 있는 경우 깔끔하게 표현 가능. 체이닝 방식(X), ',(쉼표)'로 구분
            )
            .fetchOne(); // 단건 조회

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void resultFetch(){
//        //List
//        List<Member> fetch = queryFactory
//            .selectFrom(member)
//            .fetch(); // 리스트 조회, 데이터 없으면 빈 리스트 반환
//
//        //단건
//        Member fetchOne = queryFactory
//            .selectFrom(member)
//            .fetchOne(); // 단건 조회, 결과 없으면 null, 둘 이상이면 NonUniqueResultException 발생(Querydsl 레벨)
//
//        //처음 한 건 조회
//        Member fetchFirst = queryFactory
//            .selectFrom(member)
//            .fetchFirst(); // limit(1).fetchOne() 과 같음

        //페이징에서 사용
        QueryResults<Member> results = queryFactory
            .selectFrom(member)
            .fetchResults(); // 페이징 정보 포함, total count 쿼리 추가 실행

        results.getTotal();
        List<Member> contents = results.getResults();

        // count 쿼리로 변경
        long total = queryFactory
            .selectFrom(member)
            .fetchCount(); // count 쿼리로 변경해서 count 수 조회
    }

    /**
     * 회원 정렬 순서
     * 1. 회원 나이 내림차순(desc)
     * 2. 회원 이름 올림차순(asc)
     * 단 2에서 회원 이름이 없으면 마지막에 출력(nulls last)
     */
    @Test
    public void sort(){
        em.persist(new Member(null, 100));
        em.persist(new Member("member5", 100));
        em.persist(new Member("member6", 100));

        List<Member> result = queryFactory
            .selectFrom(member)
            .where(member.age.eq(100))
            .orderBy(
                member.age.desc(),
                member.username.asc().nullsLast())
            .fetch();

        Member member5 = result.get(0);
        Member member6 = result.get(1);
        Member memberNull = result.get(2);
        assertThat(member5.getUsername()).isEqualTo("member5");
        assertThat(member6.getUsername()).isEqualTo("member6");
        assertThat(memberNull.getUsername()).isNull();
    }

    @Test
    public void paging1() {
        List<Member> result = queryFactory
            .selectFrom(member)
            .orderBy(member.username.desc())
            .offset(1) // 0부터 시작, 1이면 하나를 스킵한다는 뜻
            .limit(2)
            .fetch();

        assertThat(result.size()).isEqualTo(2);
    }

    @Test
    public void paging2() {
        QueryResults<Member> queryResults = queryFactory
            .selectFrom(member)
            .orderBy(member.username.desc())
            .offset(1) // 0부터 시작, 1이면 하나를 스킵한다는 뜻
            .limit(2)
            .fetchResults();

        assertThat(queryResults.getTotal()).isEqualTo(4);
        assertThat(queryResults.getLimit()).isEqualTo(2);
        assertThat(queryResults.getOffset()).isEqualTo(1);
        assertThat(queryResults.getResults().size()).isEqualTo(2);
    }

    @Test
    public void aggregation() {
        List<Tuple> result = queryFactory // 데이터 타입이 여러개 들어오면 Tuple 사용
            .select(
                member.count(),
                member.age.sum(),
                member.age.avg(),
                member.age.max(),
                member.age.min()
            )
            .from(member)
            .fetch();

        Tuple tuple = result.get(0);
        assertThat(tuple.get(member.count())).isEqualTo(4);
        assertThat(tuple.get(member.age.sum())).isEqualTo(60);
        assertThat(tuple.get(member.age.avg())).isEqualTo(15);
        assertThat(tuple.get(member.age.max())).isEqualTo(20);
        assertThat(tuple.get(member.age.min())).isEqualTo(10);
    }

    /**
     * 팀의 이름과 각 팀의 평균 연령을 구해라
     */
    @Test
    public void group(){
        List<Tuple> result = queryFactory
            .select(team.name, member.age.avg())
            .from(member)
            .join(member.team, team)
            .groupBy(team.name)
            .fetch();

        Tuple teamA = result.get(0);
        Tuple teamB = result.get(1);

        assertThat(teamA.get(team.name)).isEqualTo("teamA");
        assertThat(teamA.get(member.age.avg())).isEqualTo(15); //10 + 20 / 2

        assertThat(teamB.get(team.name)).isEqualTo("teamB");
        assertThat(teamA.get(member.age.avg())).isEqualTo(15); //10 + 20 / 2
    }

    /**
     * 팀 A에 소속된 모든 회원
     */
    @Test
    public void join() {
        List<Member> result = queryFactory
            .selectFrom(member)
            .join(member.team, team) //QTeam을 의미
            .where(team.name.eq("teamA"))
            .fetch();

        assertThat(result)
            .extracting("username")
            .containsExactly("member1", "member2");
    }

    /**
     * 연관관계가 없는 조인 (세타조인)
     * 회원의 이름이 팀 이름과 같은 회원 조회
     */
    @Test
    public void theta_join() {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        List<Member> result = queryFactory
            .select(member)
            .from(member, team)
            .where(member.username.eq(team.name))
            .fetch();

        assertThat(result)
            .extracting("username")
            .containsExactly("teamA", "teamB");
    }

    /**
     * 예) 회원과 팀을 조인하면서, 팀 이름이 teamA인 팀만 조인, 회원은 모두 조회
     * JPQL: SELECT m, t FROM Member m LEFT JOIN m.team t on t.name = 'teamA'
     * SQL: SELECT m.*, t.* FROM Member m LEFT JOIN Team t on m.TEAM_ID=t.id and t.name='teamA'
     */
    @Test
    public void join_on_filtering(){
        List<Tuple> result = queryFactory
            .select(member, team)
            .from(member)
            .leftJoin(member.team, team).on(team.name.eq("teamA"))
//            .join(member.team, team)
//            .where(team.name.eq("teamA"))
            /**
             * on 절을 이용해 조인 대상 필터링 시, 외부조인이 아니라 내부조인(inner join)을 사용하면 where 절에서 필터링 하는 것과 동일.
             * 내부조인 이면 익숙한 where 절로 해결하고, 외부조인이 필요하면 on절 사용
             */
            .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /**
     * 연관관계가 없는 엔티티 외부 조인
     * 회원의 이름이 팀 이름과 같은 대상을 외부 조인
     */
    @Test
    public void join_on_no_relation() {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        List<Tuple> result = queryFactory
            .select(member, team)
            .from(member)
            .leftJoin(team).on(member.username.eq(team.name)) // join()으로 바꾸면 null인 데이터는 반환하지 않는다.
            .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    @PersistenceUnit
    EntityManagerFactory emf; // fetch join 증명시 필요

    @Test
    public void fetchJoinNo() {
        em.flush();
        em.clear();

        Member findMember = queryFactory
            .selectFrom(member)
            .where(member.username.eq("member1"))
            .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("fetch join 미적용").isFalse();
    }

    @Test
    public void fetchJoinUse() {
        em.flush();
        em.clear();

        Member findMember = queryFactory
            .selectFrom(member)
            .join(member.team, team).fetchJoin() // Member를 조회할 때 연관된 Team 모두 끌어오기
            .where(member.username.eq("member1"))
            .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("fetch join 적용").isTrue();
    }

    /**
     * 나이가 가장 많은 회원 조회
     */
    @Test
    public void subQuery() {
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
            .selectFrom(member)
            .where(member.age.eq( //멤버의 나이가 같은데
                select(memberSub.age.max()) //멤버의 나이가 가장 큰 사람 == 20
                    .from(memberSub)
            ))
            .fetch();

        assertThat(result).extracting("age")
            .containsExactly(20);
    }

    /**
     * 나이가 평균 이상인 회원 조회
     */
    @Test
    public void subQueryGoe() {
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
            .selectFrom(member)
            .where(member.age.goe( //멤버의 나이가 같은데
                select(memberSub.age.avg()) //멤버의 나이 평균
                    .from(memberSub)
            ))
            .fetch();

        assertThat(result).extracting("age")
            .containsExactly(20, 20);
    }

    /**
     * 나이가 10살 초과인 회원 조회
     */
    @Test
    public void subQueryIn() {
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
            .selectFrom(member)
            .where(member.age.in( //멤버의 나이가
                select(memberSub.age)
                    .from(memberSub)
                    .where(memberSub.age.gt(10)) // 10살 초과
            ))
            .fetch();

        assertThat(result).extracting("age")
            .containsExactly(20, 20);
    }

    /**
     * Select 절에서 서브쿼리 사용 (모든 회원의 이름과 평균 조회)
     *
     * -- 주의사항 (from 절의 서브쿼리 한계)
     * 1. JPA JPQL 서브쿼리의 한계점으로 from 절의 서브쿼리(인라인 뷰)는 지원하지 않음.
     * 2. Querydsl 또한 마찬가지
     * 3. 하이버네이트 구현체를 사용하면 select 절의 서브 쿼리는 지원한다
     *
     * -- 해결방안
     * 1. 서브쿼리를 join으로 변경한다 (가능하다면.)
     * 2. 애플리케이션에서 쿼리를 2번 분리해서 실행한다. (성능이 중요하지 않을 때)
     * 3. nativeSQL을 사용한다.
     */
    @Test
    public void selectSubQuery() {
        QMember memberSub = new QMember("memberSub");

        List<Tuple> result = queryFactory
            .select(member.username, //JPAExpressions static import possible
                select(memberSub.age.avg())
                    .from(memberSub))
            .from(member)
            .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /**
     * Case 문
     * 단순한 조건
     */
    @Test
    public void basicCase() {
        List<String> result = queryFactory
            .select(member.age
                .when(10).then("열살")
                .when(20).then("스무살")
                .otherwise("기타"))
            .from(member)
            .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    /**
     * Case 문
     * 복잡한 조건
     * 가급적 디비에서 이런 문제를 해결하지 말고 코드단에서
     */
    @Test
    public void complexCase() {
        List<String> result = queryFactory
            .select(new CaseBuilder()
                .when(member.age.between(0, 20)).then("0 ~ 20살")
                .when(member.age.between(21, 40)).then("21 ~ 40살")
                .otherwise("기타"))
            .from(member)
            .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    /**
     * 상수 가져오기
     */
    @Test
    public void constant() {
        List<Tuple> result = queryFactory
            .select(member.username, Expressions.constant("A"))
            .from(member)
            .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /**
     * 문자 더하기
     */
    @Test
    public void concat() {
        //{username}_{age}
        List<String> result = queryFactory
            .select(member.username.concat("_").concat(member.age.stringValue())) //stringValue 쓸 일 많음. Enum 타입 같은 경우, 출력할 때 사용.
            .from(member)
            .where(member.username.eq("member1"))
            .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    /**
     * SELECT 절 프로젝션
     * SELECT 절에 나열하는 것을 프로젝션이라 함
     */
    @Test
    public void simpleProjection() {
        List<String> result = queryFactory
            .select(member.username)
            .from(member)
            .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    /**
     * Tuple 타입 프로젝션
     * 리포지토리 계층에서 사용하는 건 괜찮음.
     * !! 서비스 계층, 컨트롤러까지 넘어가는건 좋지 않다.
     */
    @Test
    public void tupleProjection() {
        List<Tuple> result = queryFactory
            .select(member.username, member.age)
            .from(member)
            .fetch();

        for (Tuple tuple : result) {
            String username = tuple.get(member.username);
            Integer age = tuple.get(member.age);
            System.out.println("username = " + username);
            System.out.println("age = " + age);
        }
    }

    /**
     * 별로임
     * new 명령어 사용 번거로움
     * 생성자 방식만 지원함
     */
    @Test
    public void findDtoByJPQL() {
        List<MemberDto> result = em.createQuery(
                "select new study.querydsl.dto.MemberDto(m.username, m.age) from Member m",
                MemberDto.class)
            .getResultList();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /**
     * Querydsl 빈 생성
     * 프로퍼티 접근
     * 필드 직접 접근
     * 생성자 사용
     */
    @Test
    public void findDtoBySetter() { //프로퍼티
        List<MemberDto> result = queryFactory
            .select(Projections.bean(MemberDto.class, member.username, member.age))
            .from(member)
            .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test
    public void findDtoByField() { //필드
        List<MemberDto> result = queryFactory
            .select(Projections.fields(MemberDto.class, member.username, member.age))
            .from(member)
            .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test
    public void findDtoByConstructor() { //생성자
        List<MemberDto> result = queryFactory
            .select(Projections.constructor(MemberDto.class, member.username, member.age))
            .from(member)
            .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /**
     * as.("별칭")
     */
    @Test
    public void findUserDto() {
        QMember memberSub = new QMember("memberSub");

        List<UserDto> result = queryFactory
            .select(Projections.fields(UserDto.class,
                member.username.as("name"),
                ExpressionUtils.as(JPAExpressions //서브쿼리는 ExpressionUtils로 감싸야 함.
                    .select(memberSub.age.max())
                    .from(memberSub), "age")))
            .from(member)
            .fetch();

        for (UserDto userDto : result) {
            System.out.println("userDto = " + userDto);
        }
    }

    /**
     * 프로젝션과 결과 반환 @QueryProjection
     * DTO가 QueryDSL 애노테이션에 의존해야 한다는 단점이 존재
     */
    @Test
    public void findDtoByQueryProjection() {
        List<MemberDto> result = queryFactory
            .select(new QMemberDto(member.username, member.age))
            .from(member)
            .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /**
     * 동적쿼리 해결 방법
     * BooleanBuilder
     * Where 다중 파라미터 사용 (이거 좋음)
     */
    @Test
    public void dynamicQuery_BooleanBuilder() throws Exception {
        String usernameParam = "member1";
        Integer ageParam = null;

        List<Member> result = searchMember1(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember1(String usernameCond, Integer ageCond) {
        BooleanBuilder builder = new BooleanBuilder();
        // BooleanBuilder builder = new BooleanBuilder(member.username.eq(usernameCond)); //고정이라면 초기값을 넣을 수 있음
        if(usernameCond != null) {
            builder.and(member.username.eq(usernameCond));
        }

        if(ageCond != null) {
            builder.and(member.age.eq(ageCond));
        }

        return queryFactory
            .selectFrom(member)
            .where(builder)
            .fetch();
    }

    @Test
    public void dynamicQuery_whereParam() throws Exception {
        String usernameParam = "member1";
        Integer ageParam = 10;

        List<Member> result = searchMember2(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember2(String usernameCond, Integer ageCond) {
        return queryFactory
            .selectFrom(member)
            .where(usernameEq(usernameCond), ageEq(ageCond))
//            .where(allEq(usernameCond, ageCond))
            .fetch();
    }

    private BooleanExpression usernameEq(String usernameCond) {
        return usernameCond != null ? member.username.eq(usernameCond) : null;
    }

    private BooleanExpression ageEq(Integer ageCond) {
        return ageCond != null ? member.age.eq(ageCond) : null;
    }

    //광고 상태가 isValid, 날짜가 IN: isServiceable
    private BooleanExpression allEq(String usernameCond, Integer ageCond) { //조립, 재사용(모듈화 가능)
        return usernameEq(usernameCond).and(ageEq(ageCond));
//        return isValid(~~~).and(DateBetweenIn(ageCond));
    }

    /**
     * 수정, 삭제 배치 쿼리
     * bulk 연산은 영속성 컨텍스트에 올라가 있기 때문에 디비상태와 동기화해야 함. (영속성 컨텍스트가 우선권을 가짐)
     */
    // 쿼리 한번으로 데이터 수정
    @Test
    @Commit
    public void bulkUpdate() {
        //member1 = 10 -> 비회원
        //member2 = 20 -> 비회원

        long count = queryFactory
            .update(member)
            .set(member.username, "비회원")
            .where(member.age.lt(28))
            .execute();
        em.flush();
        em.clear(); //영속성 컨텍스트 초기화

        List<Member> result = queryFactory
            .selectFrom(member)
            .fetch();

        for (Member member1 : result) {
            System.out.println("member1 = " + member1);
        }
    }

    @Test
    public void bulkAdd() {
        queryFactory
            .update(member)
            .set(member.age, member.age.add(1))
//            .set(member.age, member.age.multiply(2))
            .execute();
    }

    @Test
    public void bulkDelete() {
        queryFactory
            .delete(member)
            .where(member.age.gt(18))
            .execute();
    }

    /**
     * SQL Function 호출하기
     * JPA와 같이 Dialect에 등록된 내용만 호출할 수 있다.
     */
    @Test
    public void sqlFunction() {
        List<String> result = queryFactory
            .select(Expressions.stringTemplate(
                "function('replace', {0}, {1}, {2})",
                member.username, "member", "M"))
            .from(member)
            .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    public void sqlFunction2() {
        List<String> result = queryFactory
            .select(member.username)
            .from(member)
            .where(member.username.eq(member.username.lower()))
//                Expressions.stringTemplate("function('lower', {0})", member.username)))
            .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }
}
