"""Real local target and poisoned proxy: loopback credentials must never reach a proxy."""
import http.server
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import threading
import unittest

SCRIPT=Path(__file__).resolve().parents[2]/'tools/demo_oa_result.py'

class DemoCallbackTest(unittest.TestCase):
    def test_loopback_callback_bypasses_proxy_without_disclosing_token(self):
        received=[];forwarded=[]
        class Target(http.server.BaseHTTPRequestHandler):
            def do_POST(self):
                received.append((self.path,self.headers.get('Authorization'),json.loads(self.rfile.read(int(self.headers['Content-Length'])))))
                raw=b'{"requestId":"request1","approvalResult":"APPROVED","executionStatus":"PENDING"}'
                self.send_response(202);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(raw)));self.end_headers();self.wfile.write(raw)
            def log_message(self,*args): pass
        class Poison(http.server.BaseHTTPRequestHandler):
            def do_POST(self):
                forwarded.append(self.headers.get('Authorization'));self.send_response(502);self.end_headers()
            def log_message(self,*args): pass
        with http.server.ThreadingHTTPServer(('127.0.0.1',0),Target) as target,http.server.ThreadingHTTPServer(('127.0.0.1',0),Poison) as proxy,tempfile.TemporaryDirectory() as directory:
            threads=[threading.Thread(target=s.serve_forever,daemon=True) for s in (target,proxy)]
            for thread in threads:thread.start()
            try:
                root=Path(directory);(root/'token').write_text('fixture-secret-not-real');(root/'request.json').write_text(json.dumps(dict(id='request1',deliveryStatus='LINKED',oaApprovalId='approval1',submittedVersion=1,contentDigest='a'*64)))
                env=os.environ.copy();env.update(http_proxy=f'http://127.0.0.1:{proxy.server_port}',https_proxy=f'http://127.0.0.1:{proxy.server_port}',no_proxy='',NO_PROXY='',RBAC_DEMO_CALLBACK_TOKEN_FILE=str(root/'token'),RBAC_DEMO_BASE_URL=f'http://127.0.0.1:{target.server_port}')
                result=subprocess.run([sys.executable,str(SCRIPT),'--request',str(root/'request.json'),'--decision','APPROVED'],env=env,capture_output=True,text=True,timeout=20)
                self.assertEqual(result.returncode,0,result.stderr);self.assertEqual(json.loads(result.stdout)['executionStatus'],'PENDING');self.assertEqual(forwarded,[]);self.assertEqual(len(received),1);self.assertEqual(received[0][0],'/api/v1/oa/final-results');self.assertEqual(received[0][1],'Bearer fixture-secret-not-real');self.assertNotIn('fixture-secret-not-real',result.stdout+result.stderr)
            finally:
                target.shutdown();proxy.shutdown()
                for thread in threads:thread.join(timeout=5)

if __name__=='__main__':unittest.main()
