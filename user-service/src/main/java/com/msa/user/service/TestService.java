package com.msa.user.service;

import com.msa.user.entity.UserTest;
import com.msa.user.repository.UserTestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TestService {
    private final UserTestRepository userTestRepository;

    public List<UserTest> getTestUsers(){
        return userTestRepository.findAll();
    }
}
