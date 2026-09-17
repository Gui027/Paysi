package com.paysi.fiscal.issuer;

public record InvoiceIssueResult(boolean succeeded, String providerRef, String number, String pdfUrl,
                                  String error) {
}
