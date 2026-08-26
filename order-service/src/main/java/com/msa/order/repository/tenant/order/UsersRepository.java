package com.msa.order.repository.tenant.order;

import com.msa.order.entity.tenant.order.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UsersRepository  extends JpaRepository<Users, String> {
}
