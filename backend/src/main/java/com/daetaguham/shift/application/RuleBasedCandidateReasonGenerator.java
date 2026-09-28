package com.daetaguham.shift.application;

import com.daetaguham.shift.application.CandidateReasonGenerator.CandidateReasonContext;

import org.springframework.stereotype.Component;

@Component
public class RuleBasedCandidateReasonGenerator implements CandidateReasonGenerator {

	@Override
	public String generate(CandidateReasonContext context) {
		if (!context.openShift() && context.gaveToMe() > 0) {
			return "이전에 " + context.gaveToMe() + "번 도와줬고, "
					+ context.dayLabel() + " " + context.timeLabel() + "에 근무 가능해요.";
		}
		if (context.openShift() && context.recentOpenShiftCount() == 0) {
			return "최근 급구 참여가 없고, " + context.dayLabel() + " "
					+ context.timeLabel() + "에 근무 가능해요.";
		}
		if (context.weekHours() <= 6.0) {
			return "해당 주 근무가 " + formatHours(context.weekHours())
					+ "시간으로 적고, 가능 시간이 맞아요.";
		}
		if (!context.sameStore()) {
			return context.storeName() + " 소속이며, 가능 시간이 맞아요.";
		}
		return "가능 시간이 맞고, 해당 주 " + formatHours(context.weekHours())
				+ "시간 근무 예정이에요.";
	}

	private String formatHours(double hours) {
		return hours == Math.rint(hours)
				? Long.toString(Math.round(hours))
				: String.format(java.util.Locale.ROOT, "%.1f", hours);
	}
}
