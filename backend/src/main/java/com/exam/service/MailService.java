package com.exam.service;

import cn.hutool.core.util.StrUtil;
import com.exam.common.Consts;
import com.exam.entity.*;
import com.exam.mapper.NotifyLogMapper;
import com.exam.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.mail.internet.MimeMessage;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MailService {

    private final JavaMailSender mailSender;
    private final NotifyLogMapper notifyLogMapper;
    private final SysUserMapper userMapper;

    @Value("${exam.mail.enabled:false}")
    private boolean enabled;
    @Value("${exam.mail.from:}")
    private String from;
    @Value("${spring.mail.host:}")
    private String mailHost;

    public MailService(JavaMailSender mailSender, NotifyLogMapper notifyLogMapper, SysUserMapper userMapper) {
        this.mailSender = mailSender;
        this.notifyLogMapper = notifyLogMapper;
        this.userMapper = userMapper;
    }

    public NotifyLog sendExamNotice(ExamProject project, Paper paper, List<ExamCandidate> candidates, Long operatorId) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        StringBuilder content = new StringBuilder();
        content.append("各位考生：\n\n");
        content.append("现通知您参加 ").append(project.getName()).append("，安排如下：\n");
        content.append("考试时间：").append(project.getStartTime().format(fmt))
                .append(" 至 ").append(project.getEndTime().format(fmt)).append("\n");
        content.append("考试时长：").append(project.getDurationMin()).append(" 分钟\n");
        content.append("考试地点：").append(StrUtil.blankToDefault(project.getLocation(), "线上")).append("\n");
        content.append("批次：").append(StrUtil.blankToDefault(project.getBatch(), "-")).append("\n");
        content.append("试卷：").append(paper.getName()).append("（共 ").append(paper.getQuestionCount())
                .append(" 题，总分 ").append(paper.getTotalScore()).append(" 分）\n\n");
        content.append("考生名单：\n");
        for (ExamCandidate c : candidates) {
            content.append("  ").append(c.getName()).append("  ").append(maskIdCard(c.getIdCard()))
                    .append("  ").append(StrUtil.nullToEmpty(c.getVendor())).append("\n");
        }
        content.append("\n请携带有效身份证件，使用本人身份证号与姓名登录考试系统作答。\n");
        content.append("考试开始后超时系统将自动交卷，请合理安排时间。\n\n招聘管理组");

        List<String> to = candidates.stream().map(ExamCandidate::getEmail)
                .filter(StrUtil::isNotBlank).distinct().collect(Collectors.toList());
        // 抄送：属地招聘管理员(项目管理部) + 用人部门招聘负责人
        List<SysUser> ccUsers = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, 1)
                .and(w -> w.eq(SysUser::getRole, Consts.ROLE_PM)
                        .or(x -> x.eq(SysUser::getRole, Consts.ROLE_DEPT).eq(SysUser::getDeptId, project.getDeptId()))));
        List<String> cc = ccUsers.stream().map(SysUser::getEmail)
                .filter(StrUtil::isNotBlank).distinct().collect(Collectors.toList());

        String subject = "【笔试通知】" + project.getName() + "（" + project.getStartTime().format(fmt) + "）";
        NotifyLog notifyLog = new NotifyLog();
        notifyLog.setProjectId(project.getId());
        notifyLog.setType("EMAIL");
        notifyLog.setToList(String.join(",", to));
        notifyLog.setCcList(String.join(",", cc));
        notifyLog.setSubject(subject);
        notifyLog.setContent(content.toString());
        notifyLog.setOperatorId(operatorId);

        if (enabled && StrUtil.isNotBlank(mailHost) && !to.isEmpty()) {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(from);
                helper.setTo(to.toArray(new String[0]));
                if (!cc.isEmpty()) helper.setCc(cc.toArray(new String[0]));
                helper.setSubject(subject);
                helper.setText(content.toString());
                mailSender.send(message);
                notifyLog.setStatus("SENT");
            } catch (Exception e) {
                log.error("邮件发送失败", e);
                notifyLog.setStatus("FAILED");
                notifyLog.setError(StrUtil.maxLength(e.getMessage(), 400));
            }
        } else {
            notifyLog.setStatus("SIMULATED");
            notifyLog.setError("未启用真实邮件(exam.mail.enabled/spring.mail.host)，通知已模拟发送并存档");
        }
        notifyLogMapper.insert(notifyLog);
        return notifyLog;
    }

    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 10) return idCard;
        return idCard.substring(0, 6) + "********" + idCard.substring(idCard.length() - 4);
    }
}