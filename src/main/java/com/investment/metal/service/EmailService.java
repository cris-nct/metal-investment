package com.investment.metal.service;

import com.investment.metal.MessageKey;
import com.investment.metal.common.MailParameterBuilder;
import com.investment.metal.common.MailTemplates;
import com.investment.metal.common.MetalType;
import com.investment.metal.common.Util;
import com.investment.metal.database.Alert;
import com.investment.metal.database.Customer;
import com.investment.metal.dto.UserMetalInfoDto;
import com.investment.metal.exceptions.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@Service
public class EmailService extends AbstractService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmailService.class);

    @Value("${spring.application.name}")
    private String appName;

    @Value("${spring.mail.from}")
    private String emailFrom;

    @Value("${spring.mail.host}")
    private String host;

    @Value("${spring.mail.port}")
    private String port;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private HttpServletRequest request;

    public void sendMailWithCode(Customer user, int codeGenerated) {
        final String ip = Util.getClientIpAddress(request);
        final String emailContent = MailParameterBuilder.newInstance(MailTemplates.VERIFICATION_CODE)
                .replace("{user}", user.getUsername())
                .replace("{code}", codeGenerated)
                .replace("{ip}", ip)
                .build();
        this.sendMail(user.getEmail(), appName, emailContent);
    }

    public void sendMailWithProfit(UserProfit userInfo, Alert alert) {
        Customer user = userInfo.getUser();
        MetalType metalType = alert.getMetalType();
        String emailContent = MailParameterBuilder.newInstance(MailTemplates.ALERT)
                .replace("{user}", user.getUsername())
                .replace("{metal}", metalType.name().toLowerCase())
                .replace("{metalSymbol}", metalType.getSymbol())
                .replace("{expression}", alert.getExpression())
                .replace("{amount}", userInfo.getMetalAmount())
                .replaceDouble("{cost}", userInfo.getOriginalCost(), 2)
                .replaceDouble("{costNow}", userInfo.getCurrentCost(), 2)
                .replaceDouble("{profit}", userInfo.getProfit(), 2)
                .build();

        this.sendMail(user.getEmail(), appName, emailContent);
    }

    public void sendStatusNotification(Customer user, Map<String, UserMetalInfoDto> userProfit) {
        MailParameterBuilder emailContentBuilder = MailParameterBuilder.newInstance(MailTemplates.STATUS)
                .replace("{user}", user.getUsername());
        for (MetalType metalType : MetalType.values()) {
            String metalSymbol = metalType.getSymbol();
            UserMetalInfoDto info = userProfit.get(metalSymbol);
            final String part;
            if (info != null) {
                final double amountGrams = info.getAmountPurchased() * Util.OUNCE * 1000;
                part = MailParameterBuilder.newInstance(MailTemplates.STATUS_PART)
                        .replace("{amountOunces}", info.getAmountPurchased())
                        .replaceDouble("{amountGrams}", amountGrams, 3)
                        .replace("{metal}", metalType.name())
                        .replace("{metalSymbol}", metalSymbol)
                        .replaceDouble("{cost}", info.getCostPurchased(), 2)
                        .replaceDouble("{priceNow}", info.getCostNow(), 2)
                        .replaceDouble("{profit}", info.getProfit(), 2)
                        .build();
            } else {
                part = "";
            }
            emailContentBuilder = emailContentBuilder.replace("{" + metalSymbol + "}", part);
        }

        this.sendMail(user.getEmail(), appName, emailContentBuilder.build());
    }

    private void sendMail(String toEmail, String subject, String message) throws BusinessException {
        Exception cause = null;
        LOGGER.info("Trying to send email from " + emailFrom + " to " + toEmail + " on host " + host + ":" + port);
        for (int i = 0; i < 3; i++) {
            try {
                MimeMessage msg = this.mailSender.createMimeMessage();
                MimeMessageHelper mailMessage = new MimeMessageHelper(msg, true);
                mailMessage.setTo(toEmail);
                mailMessage.setSubject(subject);
                mailMessage.setText(message, true);
                mailMessage.setFrom(emailFrom);
                this.mailSender.send(msg);
                cause = null;
                break;
            } catch (Exception e) {
                Util.sleep(2000);
                cause = e;
            }
        }
        if (cause != null) {
            LOGGER.error(cause.getMessage(), cause);
            throw this.exceptionService
                    .createBuilder(MessageKey.FAIL_TO_SEND_EMAIL)
                    .setExceptionCause(cause)
                    .setArguments(toEmail)
                    .build();
        }
    }

}