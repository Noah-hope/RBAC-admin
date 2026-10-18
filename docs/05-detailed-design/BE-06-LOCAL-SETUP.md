# BE-06 本机启用与后续组员接入

先完成[基座本机配置](FOUNDATION-USAGE.md)。BE-06默认关闭身份业务装配；关闭时admin/integration/oa入口返回503，健康入口仍可访问。正式业务开发需启用身份，不能用关闭开关跳过认证。

## 在本机生成开发用密钥

每个成员使用自己的本机数据库时可独立生成；同一数据库/服务部署的实例应使用同一套管理员签名与信任配置。这里的RSA/JWT密钥与BE-05的HMAC密钥、数据库密码是三个不同用途的秘密。

在PowerShell执行下例，仅将文件写到本机用户应用目录，不打印密钥。脚本只生成开发密钥，不创建数据库账号或管理员。已有密钥时会拒绝覆盖；不要反复生成来解决启动问题。

```powershell
$taskKeyDir = Join-Path $env:LOCALAPPDATA 'rbac-admin/identity-keys'
New-Item -ItemType Directory -Path $taskKeyDir -Force | Out-Null
function ConvertTo-LocalBase64Url([byte[]] $bytes) {
    [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}
foreach ($taskName in @('admin', 'business', 'oa')) {
    $taskSigning = Join-Path $taskKeyDir "$taskName-signing.json"
    $taskTrust = Join-Path $taskKeyDir "$taskName-trust.json"
    if ((Test-Path -LiteralPath $taskSigning) -or (Test-Path -LiteralPath $taskTrust)) {
        throw "已有 $taskName 密钥，请使用现有文件或另选目录。"
    }
    $taskRsa = [System.Security.Cryptography.RSA]::Create()
    try {
        $taskRsa.KeySize = 2048
        $taskParameters = $taskRsa.ExportParameters($true)
        $taskJwk = @{ kty='RSA'; kid="local-$taskName-v1"; use='sig'; alg='RS256' }
        $taskFields = @{ n='Modulus'; e='Exponent'; d='D'; p='P'; q='Q'; dp='DP'; dq='DQ'; qi='InverseQ' }
        foreach ($taskField in $taskFields.Keys) {
            $taskJwk[$taskField] = ConvertTo-LocalBase64Url $taskParameters.($taskFields[$taskField])
        }
        $taskPublic = @{}
        foreach ($taskField in @('kty','kid','use','alg','n','e')) { $taskPublic[$taskField] = $taskJwk[$taskField] }
        $taskEncoding = [System.Text.UTF8Encoding]::new($false)
        [IO.File]::WriteAllText($taskSigning, (@{ keys=@($taskJwk) } | ConvertTo-Json -Depth 5), $taskEncoding)
        [IO.File]::WriteAllText($taskTrust, (@{ keys=@($taskPublic) } | ConvertTo-Json -Depth 5), $taskEncoding)
    } finally { $taskRsa.Dispose() }
}
Write-Output "本机开发密钥已写入：$taskKeyDir"
```

三个`*-signing.json`含私钥，只在本机保管；应用仅需管理员私钥，业务/OA仅配公钥。未来服务调用工具使用相应服务私钥，BE-31负责该工具，不由管理员登录签发服务token。生产密钥由实际部署管理，不复用这里的开发文件。

## IDEA运行配置

打开 **运行 → 编辑配置 → Spring Boot下的BackendApplication → 修改选项 → 环境变量**。JRE仍选21，工作目录仍为backend；在原数据库/幂等变量基础上添加：

| 名称 | 填写内容 |
| --- | --- |
| RBAC_IDENTITY_ENABLED | true |
| RBAC_ADMIN_KEY_ID | local-admin-v1 |
| RBAC_ADMIN_JWK_SET | 上述目录中admin-signing.json的完整路径 |
| RBAC_BUSINESS_JWK_SET | business-trust.json的完整路径 |
| RBAC_OA_JWK_SET | oa-trust.json的完整路径 |

默认issuer分别为`urn:rbac:admin`、`urn:rbac:business`、`urn:rbac:oa`；对接受控调用方时分别用RBAC_ADMIN_ISSUER/RBAC_BUSINESS_ISSUER/RBAC_OA_ISSUER覆盖，并使签发方一致。不能将服务私钥文件误填到公钥配置，启用后缺失/非法配置会拒绝启动。独立迁移配置不要添加身份启用变量。

重新加载Maven后启动，检查健康UP。BE-06没有管理员初始化接口，不自动向开发库插入测试账号；初始管理员由BE-32的受控初始化交付。现阶段可运行BE-06的隔离测试验证真实登录，不能把健康UP当登录验收。请求中的密码和响应token不要粘贴到共享记录。

## 给B/C/D的开发样例

- Controller/DTO照`web/identity/controller`和`web/identity/dto`，分别在自己的`web/catalog`、`web/change`等领域中建文件。Service、Repository照`identity/service`和`identity/repository`放到各自业务域；无需等待A创建空目录。
- HTTP层可使用`@AuthenticationPrincipal Jwt`接收已验证JWT。进入业务后仍需在自己的ReadBoundary/CommandBoundary回调中调用CurrentIdentityReader，确保身份与业务使用同一个当前快照/guard；HTTP校验不代替资源范围授权。
- 管理员正常会话才能访问普通业务。范围选择、单项任命与覆盖策略待A的BE-08，组员不得从JWT或请求body自报管理范围，也不能把后台身份当业务角色。
- 集成/回调校验还读取service_identity当前状态及绑定。业务token需要systemId；OA token需要oaInstanceId且匹配当前服务身份ID。只声明认证入口，不代表BE-18/24接口已实现。
- 账号创建/停用/重置由BE-07复用IdentityRepository的受控写端口；更新凭据/周期与撤销会话必须同事务完成。跨域生命周期仍由BE-27按设计推进，恢复不复活旧会话。
- 真实数据库测试使用IsolatedMySqlDatabase和专属临时MySQL；fixture位于测试包，不创建正式演示接口、不使用开发库灌测试数据。自动测试先RED后实现，每项由负责人自行确认。
