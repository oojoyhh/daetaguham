package com.daetaguham.shift.application;

public interface CandidateReasonGenerator {

	String generate(CandidateReasonContext context);

	record CandidateReasonContext(
			String storeName,
			boolean sameStore,
			double weekHours,
			int gaveToMe,
			int recentOpenShiftCount,
			String dayLabel,
			String timeLabel,
			boolean openShift
	) {
	}
}
