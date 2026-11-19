package com.nordia.tasks.repository;

import com.nordia.tasks.model.Member;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public class MemberRepository {

    private final JdbcTemplate jdbc;

    public MemberRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Member> findAll() {
        return jdbc.query("SELECT id, name FROM member ORDER BY id",
                (rs, i) -> new Member(rs.getString("id"), rs.getString("name")));
    }
}
