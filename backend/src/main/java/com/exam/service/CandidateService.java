package com.exam.service;

import cn.hutool.core.util.StrUtil;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exam.common.BusinessException;
import com.exam.common.Consts;
import com.exam.entity.ExamCandidate;
import com.exam.entity.ExamProject;
import com.exam.mapper.ExamCandidateMapper;
import com.exam.mapper.ExamRecordMapper;
import com.exam.entity.ExamRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.*;

@Service
public class CandidateService {

    private final ExamCandidateMapper candidateMapper;
    private final ExamRecordMapper recordMapper;
    private final HrClient hrClient;
    private final ProjectService projectService;

    public CandidateService(ExamCandidateMapper candidateMapper, ExamRecordMapper recordMapper,
                            HrClient hrClient, ProjectService projectService) {
        this.candidateMapper = candidateMapper;
        this.recordMapper = recordMapper;
        this.hrClient = hrClient;
        this.projectService = projectService;
    }

    public List<ExamCandidate> list(Long projectId) {
        projectService.get(projectId);
        return candidateMapper.selectList(new LambdaQueryWrapper<ExamCandidate>()
                .eq(ExamCandidate::getProjectId, projectId).orderByAsc(ExamCandidate::getId));
    }

    public ExamCandidate addManual(Long projectId, ExamCandidate c) {
        projectService.checkWrite(projectService.get(projectId));
        validate(c);
        c.setId(null);
        c.setProjectId(projectId);
        c.setSource("MANUAL");
        c.setStatus("NOT_ATTENDED");
        insertUnique(c);
        return c;
    }

    private void validate(ExamCandidate c) {
        if (StrUtil.isBlank(c.getName())) throw new BusinessException("姓名不能为空");
        if (StrUtil.isBlank(c.getIdCard()) || c.getIdCard().trim().length() < 15)
            throw new BusinessException("身份证号不合法");
        c.setIdCard(c.getIdCard().trim().toUpperCase());
        c.setGroupName(StrUtil.trimToNull(c.getGroupName()));
    }

    private void insertUnique(ExamCandidate c) {
        Long exist = candidateMapper.selectCount(new LambdaQueryWrapper<ExamCandidate>()
                .eq(ExamCandidate::getProjectId, c.getProjectId())
                .eq(ExamCandidate::getIdCard, c.getIdCard()));
        if (exist != null && exist > 0) {
            throw new BusinessException("考生 " + c.getName() + "（" + MailService.maskIdCard(c.getIdCard()) + "）已在名单中");
        }
        candidateMapper.insert(c);
    }

    public Map<String, Object> hrSync(Long projectId) {
        ExamProject project = projectService.get(projectId);
        projectService.checkWrite(project);
        List<ExamCandidate> pulled = hrClient.pullCandidates(project);
        int added = 0, skipped = 0;
        for (ExamCandidate c : pulled) {
            try {
                validate(c);
                insertUnique(c);
                added++;
            } catch (BusinessException e) {
                skipped++;
            }
        }
        Map<String, Object> res = new HashMap<>();
        res.put("added", added);
        res.put("skipped", skipped);
        res.put("total", pulled.size());
        res.put("mock", !hrClient.realEnabled());
        return res;
    }

    /** Excel 列: 姓名 身份证号 手机 邮箱 供应商 组名(编组模式) */
    public Map<String, Object> importExcel(Long projectId, MultipartFile file) throws IOException {
        projectService.checkWrite(projectService.get(projectId));
        List<Map<Integer, String>> rows = EasyExcel.read(file.getInputStream()).sheet().headRowNumber(1).doReadSync();
        int success = 0;
        List<String> errors = new ArrayList<>();
        int rowNo = 1;
        for (Map<Integer, String> row : rows) {
            rowNo++;
            try {
                ExamCandidate c = new ExamCandidate();
                c.setName(row.get(0));
                c.setIdCard(row.get(1));
                c.setPhone(row.get(2));
                c.setEmail(row.get(3));
                c.setVendor(row.get(4));
                c.setGroupName(row.get(5));
                validate(c);
                c.setProjectId(projectId);
                c.setSource("EXCEL");
                c.setStatus("NOT_ATTENDED");
                insertUnique(c);
                success++;
            } catch (Exception e) {
                errors.add("第" + rowNo + "行: " + e.getMessage());
            }
        }
        Map<String, Object> res = new HashMap<>();
        res.put("success", success);
        res.put("fail", errors.size());
        res.put("errors", errors);
        return res;
    }

    public void delete(Long projectId, Long candidateId) {
        projectService.checkWrite(projectService.get(projectId));
        ExamCandidate c = candidateMapper.selectById(candidateId);
        if (c == null || !Objects.equals(c.getProjectId(), projectId)) throw new BusinessException("考生不存在");
        Long records = recordMapper.selectCount(new LambdaQueryWrapper<ExamRecord>()
                .eq(ExamRecord::getCandidateId, candidateId));
        if (records != null && records > 0) throw new BusinessException("该考生已有考试记录，不能移除");
        candidateMapper.deleteById(candidateId);
    }

    /** 编辑考生资料与分组（身份证号作为身份标识不可改） */
    public ExamCandidate update(Long projectId, Long candidateId, ExamCandidate data) {
        projectService.checkWrite(projectService.get(projectId));
        ExamCandidate c = candidateMapper.selectById(candidateId);
        if (c == null || !Objects.equals(c.getProjectId(), projectId)) throw new BusinessException("考生不存在");
        if (StrUtil.isBlank(data.getName())) throw new BusinessException("姓名不能为空");
        c.setName(data.getName().trim());
        c.setPhone(data.getPhone());
        c.setEmail(data.getEmail());
        c.setVendor(data.getVendor());
        c.setGroupName(StrUtil.trimToNull(data.getGroupName()));
        candidateMapper.updateById(c);
        return c;
    }

    public void export(Long projectId, HttpServletResponse response) throws IOException {
        ExamProject project = projectService.get(projectId);
        boolean groupMode = Consts.MODE_GROUP.equals(project.getMode());
        List<ExamCandidate> list = list(projectId);
        List<List<String>> head = new ArrayList<>(Arrays.asList(
                Collections.singletonList("姓名"), Collections.singletonList("身份证号"),
                Collections.singletonList("手机"), Collections.singletonList("邮箱"),
                Collections.singletonList("供应商")));
        if (groupMode) head.add(Collections.singletonList("组名"));
        head.add(Collections.singletonList("来源"));
        head.add(Collections.singletonList("状态"));
        List<List<Object>> data = new ArrayList<>();
        for (ExamCandidate c : list) {
            List<Object> row = new ArrayList<>(Arrays.asList(c.getName(), c.getIdCard(), c.getPhone(),
                    c.getEmail(), c.getVendor()));
            if (groupMode) row.add(StrUtil.nullToEmpty(c.getGroupName()));
            row.add(sourceName(c.getSource()));
            row.add(statusName(c.getStatus()));
            data.add(row);
        }
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode("考生名单", "UTF-8") + ".xlsx");
        EasyExcel.write(response.getOutputStream()).head(head).sheet("考生名单").doWrite(data);
    }

    /** 导入模板按项目模式区分：个人模式无组名列，编组模式含组名列 */
    public void downloadTemplate(Long projectId, HttpServletResponse response) throws IOException {
        ExamProject project = projectService.get(projectId);
        boolean groupMode = Consts.MODE_GROUP.equals(project.getMode());
        List<List<String>> head = new ArrayList<>(Arrays.asList(
                Collections.singletonList("姓名"), Collections.singletonList("身份证号"),
                Collections.singletonList("手机"), Collections.singletonList("邮箱"),
                Collections.singletonList("供应商")));
        List<Object> demo = new ArrayList<>(Arrays.asList(
                "示例考生", "110101199501011234", "13800000000", "demo@example.com", "示例供应商"));
        if (groupMode) {
            head.add(Collections.singletonList("组名"));
            demo.add("A组");
        }
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-Disposition", "attachment;filename=candidate_template.xlsx");
        EasyExcel.write(response.getOutputStream()).head(head).sheet("模板").doWrite(Collections.singletonList(demo));
    }

    public static String sourceName(String s) {
        if ("HR".equals(s)) return "人事系统";
        if ("EXCEL".equals(s)) return "Excel导入";
        return "手动添加";
    }

    public static String statusName(String s) {
        if ("ATTENDED".equals(s)) return "考试中";
        if ("SUBMITTED".equals(s)) return "已交卷";
        return "未参加";
    }
}