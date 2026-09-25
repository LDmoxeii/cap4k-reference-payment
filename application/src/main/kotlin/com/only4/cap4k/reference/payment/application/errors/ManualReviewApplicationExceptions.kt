package com.only4.cap4k.reference.payment.application.errors

class ManualReviewNotFoundException(reviewId: String) : PaymentApplicationException(
    code = "MANUAL_REVIEW_NOT_FOUND",
    message = "未找到人工核对事项 $reviewId",
    details = mapOf("reviewId" to reviewId),
)
