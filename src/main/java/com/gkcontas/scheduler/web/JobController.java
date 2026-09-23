package com.gkcontas.scheduler.web;

import com.gkcontas.scheduler.dto.JobExecutionResponse;
import com.gkcontas.scheduler.dto.JobSummaryResponse;
import com.gkcontas.scheduler.dto.RateVsDelayReport;
import com.gkcontas.scheduler.dto.UpdateCronRequest;
import com.gkcontas.scheduler.dto.UpdateEnabledRequest;
import com.gkcontas.scheduler.job.RateVsDelayDemo;
import com.gkcontas.scheduler.service.JobAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/jobs")
public class JobController {

    private final JobAdminService jobAdminService;
    private final RateVsDelayDemo rateVsDelayDemo;

    public JobController(JobAdminService jobAdminService, RateVsDelayDemo rateVsDelayDemo) {
        this.jobAdminService = jobAdminService;
        this.rateVsDelayDemo = rateVsDelayDemo;
    }

    @GetMapping
    public List<JobSummaryResponse> listJobs() {
        return jobAdminService.listJobs();
    }

    /** Takes effect immediately — no restart, no redeploy. */
    @PutMapping("/{jobName}/schedule")
    public JobSummaryResponse updateSchedule(@PathVariable String jobName,
                                             @Valid @RequestBody UpdateCronRequest request) {
        return jobAdminService.updateCron(jobName, request.cronExpression());
    }

    @PutMapping("/{jobName}/enabled")
    public JobSummaryResponse updateEnabled(@PathVariable String jobName,
                                            @Valid @RequestBody UpdateEnabledRequest request) {
        return jobAdminService.setEnabled(jobName, request.enabled());
    }

    @PostMapping("/{jobName}/trigger")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void trigger(@PathVariable String jobName) {
        jobAdminService.trigger(jobName);
    }

    @GetMapping("/{jobName}/executions")
    public List<JobExecutionResponse> executions(@PathVariable String jobName,
                                                 @RequestParam(defaultValue = "20") @Min(1) @Max(200) int limit) {
        return jobAdminService.executions(jobName, limit);
    }

    @GetMapping("/rate-vs-delay-demo")
    public RateVsDelayReport rateVsDelayDemo() {
        return rateVsDelayDemo.report();
    }
}
