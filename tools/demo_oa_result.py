#!/usr/bin/env python3
"""使用实际申请详情与受控OA凭据演示原最终结果回调。"""
import argparse
import datetime
import json
import os
from pathlib import Path
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--request", required=True, type=Path, help="GET申请详情保存的JSON文件")
    parser.add_argument("--decision", required=True, choices=("APPROVED", "REJECTED"))
    args = parser.parse_args()
    try:
        base = os.environ["RBAC_DEMO_BASE_URL"].rstrip("/")
        parsed = urllib.parse.urlsplit(base)
        if (parsed.username or parsed.password or parsed.query or parsed.fragment
                or parsed.path or not parsed.hostname
                or parsed.scheme not in ("http", "https")
                or parsed.scheme == "http" and parsed.hostname not in ("127.0.0.1", "localhost", "::1")):
            raise ValueError()
        token_file = Path(os.environ["RBAC_DEMO_CALLBACK_TOKEN_FILE"])
        if token_file.stat().st_size > 4096 or args.request.stat().st_size > 2_000_000:
            raise ValueError()
        token = token_file.read_text(encoding="utf-8").strip()
        if not token or "\n" in token or "\r" in token:
            raise ValueError()
        request = json.loads(args.request.read_text(encoding="utf-8"))
        if (request["deliveryStatus"] != "LINKED" or not request["oaApprovalId"]
                or request["submittedVersion"] is None or not request["contentDigest"]):
            raise ValueError()
        payload = dict(eventId=str(uuid.uuid4()), requestId=request["id"],
                       oaApprovalId=request["oaApprovalId"], submittedVersion=request["submittedVersion"],
                       contentDigest=request["contentDigest"], decision=args.decision, isFinal=True,
                       decidedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),
                       comment="演示OA最终结果")
        http = urllib.request.Request(base + "/api/v1/oa/final-results",
                                      data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
                                      headers={"Content-Type": "application/json", "Authorization": "Bearer " + token},
                                      method="POST")
        # 不跟随重定向，避免把可信凭据发送到另一地址。
        class NoRedirect(urllib.request.HTTPRedirectHandler):
            def redirect_request(self, req, fp, code, msg, headers, newurl):
                return None
        handlers = [NoRedirect]
        # 回环演示只直连本机，避免系统/环境代理截获可信回调凭据或返回代理502。
        # 非回环HTTPS仍沿用调用者明确配置的代理；保持禁止重定向。
        if parsed.hostname in ("127.0.0.1", "localhost", "::1"):
            handlers.append(urllib.request.ProxyHandler({}))
        with urllib.request.build_opener(*handlers).open(http, timeout=15) as response:
            result = json.loads(response.read(16_384))
            print(json.dumps({"status": response.status, "requestId": result["requestId"],
                              "approvalResult": result["approvalResult"],
                              "executionStatus": result["executionStatus"]}, ensure_ascii=False))
        return 0
    except urllib.error.HTTPError as error:
        print("DEMO_CALLBACK_REFUSED: HTTP " + str(error.code), file=sys.stderr)
    except Exception:
        print("DEMO_CALLBACK_REFUSED: check live linked request, trusted token file and endpoint.", file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main())
