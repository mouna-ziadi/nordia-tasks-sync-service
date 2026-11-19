package com.nordia.tasks.api;

import com.nordia.tasks.model.Result;
import com.nordia.tasks.repository.MemberRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** No business rule here, so no service layer: a pass-through service would add nothing. */
@RestController
public class MemberController {

    private final MemberRepository members;

    public MemberController(MemberRepository members) {
        this.members = members;
    }

    @GetMapping("/members")
    public ResponseEntity<String> list() {
        return Responses.of(Result.ok(members.findAll()));
    }
}
