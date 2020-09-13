package com.investment.metal;


import com.investment.metal.common.Util;
import com.investment.metal.database.Customer;
import com.investment.metal.dto.ResetPasswordDto;
import com.investment.metal.dto.SimpleMessageDto;
import com.investment.metal.dto.UserLoginDto;
import com.investment.metal.encryption.AbstractHandShakeEncryptor;
import com.investment.metal.exceptions.NoRollbackBusinessException;
import com.investment.metal.service.AccountService;
import com.investment.metal.service.BannedAccountsService;
import com.investment.metal.service.BlockedIpService;
import com.investment.metal.service.LoginService;
import com.investment.metal.service.exception.ExceptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PublicApiController {

    @Autowired
    private AccountService accountService;

    @Autowired
    private BannedAccountsService bannedAccountsService;

    @Autowired
    private BlockedIpService blockedIpService;

    @Autowired
    private LoginService loginService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ExceptionService exceptionService;

    @Autowired
    private AbstractHandShakeEncryptor handShakeEncryptor;

    @RequestMapping(value = "/userRegistration", method = RequestMethod.POST)
    @Transactional(noRollbackFor = NoRollbackBusinessException.class)
    public ResponseEntity<SimpleMessageDto> userRegistration(
            @RequestHeader("username") String username,
            @RequestHeader("password") String password,
            @RequestHeader("email") String email,
            @RequestHeader(value = "hs", defaultValue = "") String hs
    ) {
        this.handShakeEncryptor.check(hs);
        if (!Util.isValidEmailAddress(email)) {
            throw this.exceptionService
                    .createBuilder(MessageKey.INVALID_REQUEST)
                    .setArguments("Invalid email address!")
                    .build();
        }
        Customer user = this.accountService.registerNewUser(username, this.passwordEncoder.encode(password), email);
        this.loginService.validateAccount(user, false);
        SimpleMessageDto dto = new SimpleMessageDto("An email was sent to " + email + " with a code. Call validation request with that code");
        return new ResponseEntity<>(dto, HttpStatus.OK);
    }

    @RequestMapping(value = "/validateAccount", method = RequestMethod.POST)
    @Transactional(noRollbackFor = NoRollbackBusinessException.class)
    public ResponseEntity<SimpleMessageDto> validateAccount(
            @RequestHeader("username") final String username,
            @RequestHeader("code") final int code,
            @RequestHeader(value = "hs", defaultValue = "") String hs
    ) {
        this.handShakeEncryptor.check(hs);
        Customer user = this.accountService.findByUsername(username);
        this.checkBannedOrBlocked(user.getId());

        this.loginService.verifyCode(user.getId(), code);

        SimpleMessageDto dto = new SimpleMessageDto("The account was validated. You can log in now.");
        return new ResponseEntity<>(dto, HttpStatus.OK);
    }

    @RequestMapping(value = "/login", method = RequestMethod.POST)
    @Transactional(noRollbackFor = NoRollbackBusinessException.class)
    public ResponseEntity<UserLoginDto> login(
            @RequestHeader("username") final String username,
            @RequestHeader("password") final String password,
            @RequestHeader(value = "hs", defaultValue = "") String hs
    ) {
        this.handShakeEncryptor.check(hs);
        final Customer user = this.accountService.findByUsername(username);
        this.checkBannedOrBlocked(user.getId());

        if (!passwordEncoder.matches(password, user.getPassword())) {
            this.loginService.markLoginFailed(user.getId());
        }
        String token = this.loginService.login(user);
        UserLoginDto dto = new UserLoginDto(token);
        return new ResponseEntity<>(dto, HttpStatus.OK);
    }

    @RequestMapping(value = "/resetPassword", method = RequestMethod.POST)
    @Transactional(noRollbackFor = NoRollbackBusinessException.class)
    public ResponseEntity<ResetPasswordDto> resetPassword(
            @RequestHeader("email") final String email,
            @RequestHeader(value = "hs", defaultValue = "") String hs
    ) {
        this.handShakeEncryptor.check(hs);
        if (!Util.isValidEmailAddress(email)) {
            throw this.exceptionService
                    .createBuilder(MessageKey.INVALID_REQUEST)
                    .setArguments("Invalid email address!")
                    .build();
        }
        final Customer user = this.accountService.findByEmail(email);
        this.checkBannedOrBlocked(user.getId());

        this.loginService.validateAccount(user, true);
        String token = this.loginService.generateResetPasswordToken(user);
        String message = "A message with a code was sent to " + email;
        return new ResponseEntity<>(new ResetPasswordDto(token, message), HttpStatus.OK);
    }

    @RequestMapping(value = "/changePassword", method = RequestMethod.PUT)
    @Transactional(noRollbackFor = NoRollbackBusinessException.class)
    public ResponseEntity<SimpleMessageDto> changePassword(
            @RequestHeader("code") final int code,
            @RequestHeader("newPassword") final String newPassword,
            @RequestHeader("email") final String email,
            @RequestHeader("token") final String token,
            @RequestHeader(value = "hs", defaultValue = "") String hs
    ) {
        this.handShakeEncryptor.check(hs);
        if (!Util.isValidEmailAddress(email)) {
            throw this.exceptionService
                    .createBuilder(MessageKey.INVALID_REQUEST)
                    .setArguments("Invalid email address!")
                    .build();
        }
        final Customer user = this.accountService.findByEmail(email);
        this.checkBannedOrBlocked(user.getId());

        this.loginService.verifyCodeAndToken(user.getId(), code, token);
        this.accountService.updatePassword(user, this.passwordEncoder.encode(newPassword));

        SimpleMessageDto dto = new SimpleMessageDto("Password was changed successfully!");
        return new ResponseEntity<>(dto, HttpStatus.OK);
    }

    private void checkBannedOrBlocked(long userId) {
        this.bannedAccountsService.checkBanned(userId);
        this.blockedIpService.checkBlockedIP(userId);
    }
}
