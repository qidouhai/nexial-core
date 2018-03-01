-- nexial:vouchers
SELECT
	V.VOUCHER_ID AS "Voucher ID",
	V.CLIENT_ID AS "Client ID",
	EMPLOYEE_ID AS "Employee ID",
	CHECK_DATE AS "Check Date",
	PROCESS_DATE AS "Process Date",
	GROSS_AMT AS "Gross Amount",
	NET_AMT AS "Net Amount",
	T.AMOUNT AS "Tax Amount",
	V.STG_PROCESSINGLOG_ID AS "Log ID"
FROM
	STG_VOUCHER V, STG_VCH_TAX T
WHERE V.VOUCHER_ID = T.VOUCHER_ID
	AND V.TAX_YEAR = ${TAX Year}
	AND CLIENT_ID = ${Client ID}
	AND V.VOUCHER_ID = ${Voucher ID};

-- nexial:processing log
SELECT
	BATCHID AS "Batch ID",
	PROCESSINGCOMMENT AS "Comment",
	VOUCHERTIMECARDCOUNT_NEW AS "New Vouchers",
	VOUCHERTIMECARDCOUNT_UPDATED AS "Updatd Vouchers",
	VOUCHERPAYTAXCOUNT_NEW AS "New Tax Entries",
	VOUCHERPAYTAXCOUNT_UPDATED AS "Updated Tax Entries"
FROM
	STG_PROCESSINGLOG L
WHERE L.STG_PROCESSINGLOG_ID = ${vouchers}.data[0].[Log ID]
	AND (VOUCHERTIMECARDCOUNT_NEW + VOUCHERTIMECARDCOUNT_UPDATED + VOUCHERPAYTAXCOUNT_NEW + VOUCHERPAYTAXCOUNT_UPDATED)
		<= ${vouchers}.rowCount;
