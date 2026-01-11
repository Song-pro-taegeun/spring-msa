package com.msa.user.service.admin;

import com.msa.user.entity.user.Users;
import com.msa.user.repository.user.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class AdminService {
    private final UsersRepository usersRepository;

    public List<Users> getUsers(){
        return usersRepository.findAll();
    }
}
