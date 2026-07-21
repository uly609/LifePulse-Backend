package com.lifepulse.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lifepulse.auth.JwtService;
import com.lifepulse.common.BusinessException;
import com.lifepulse.common.IdGenerator;
import com.lifepulse.common.Result;
import com.lifepulse.entity.UserAccount;
import com.lifepulse.mapper.UserAccountMapper;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final IdGenerator idGenerator;

    public UserController(UserAccountMapper userAccountMapper, PasswordEncoder passwordEncoder,
                          JwtService jwtService, IdGenerator idGenerator) {
        this.userAccountMapper = userAccountMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.idGenerator = idGenerator;
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        UserAccount user = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUsername, request.username()));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException("账号或密码错误");
        }
        String token = jwtService.createToken(user.getId(), user.getUsername(), user.getRole());
        return Result.success(new LoginResponse(user.getId(), user.getUsername(), user.getRole(), token));
    }

    @PostMapping("/register")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        String username = request.username().trim();
        Long count = userAccountMapper.selectCount(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUsername, username));
        if (count > 0) {
            throw new BusinessException("该账号已存在");
        }
        UserAccount user = new UserAccount();
        user.setId(idGenerator.nextId());
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole("USER");
        user.setCreatedAt(LocalDateTime.now());
        userAccountMapper.insert(user);
        String token = jwtService.createToken(user.getId(), user.getUsername(), user.getRole());
        return Result.success(new LoginResponse(user.getId(), user.getUsername(), user.getRole(), token));
    }
}
