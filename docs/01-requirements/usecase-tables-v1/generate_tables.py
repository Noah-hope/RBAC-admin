"""生成课程格式用例表：draw.io 原生单元格、Markdown、PNG 和 HTML 预览。
运行：python docs/01-requirements/usecase-tables-v1/generate_tables.py
重新生成会覆盖本目录生成文件；手工修改 drawio 后请另存副本。
"""
import sys
GENERATE_PREVIEWS = '--previews' in sys.argv

from pathlib import Path
import html
import re
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parent
SOURCE = OUT.parent / 'usecases-v1'
ADMIN = '权限中心总管理员、权限管理员'
CASES = []

def case(n, package, actors, desc, trigger, pre, main, extensions, other):
    src = next(SOURCE.glob(f'UC-{n:02d}_*.md'))
    name = src.read_text(encoding='utf-8').splitlines()[0].split(' ', 2)[2]
    def pairs(s):
        return [tuple(line.split('|', 1)) for line in s.strip().splitlines()]
    CASES.append(dict(id=f'UC-{n:02d}', name=name, source=src.name,
        package=package, actors=actors, desc=desc, trigger=trigger, pre=pre,
        main=pairs(main), extensions=pairs(extensions), other=other))

case(1, '系统与权限目录', '权限中心总管理员',
 '维护接入系统、菜单及功能操作，供角色配置和业务权限查询使用。',
 '新系统接入，或已有系统需要增加、修改、停用权限目录。',
 '总管理员已登录；待维护内容的业务含义及归属明确。', '''
管理员选择已有系统或登记新系统。|系统名称、编码、说明
系统展示该系统的权限目录及系统进入权限。|系统进入权限、目录树、当前状态
管理员维护菜单、操作权限及菜单可见条件。|菜单层级、操作编码、功能归属、关联查看／进入页面权限
系统展示变更及对已有授权的影响。|新增、修改、启停明细及影响提示
管理员核对变更，填写原因并确认保存。|确认内容、修改原因
系统保存目录并显示维护结果。|保存结果、最新目录
''', '''
3a. 编码重复、归属错误或菜单绑定不合法：提示原因，返回第3步修正。|问题字段、规则说明
3b. 停用或恢复目录：第4步提示对子目录及已有授权的影响，确认后保存；保留历史。|受影响目录及授权
3c. 删除已引用项或跨系统迁移：拒绝，改用新增与停用方式。|拒绝原因
5a. 内容已被他人修改：重新读取并核对后提交。|最新内容、冲突提示
6a. 保存失败：本次变更不生效，提示稍后重试。|失败说明
''', '无需 OA 审批。每系统有独立进入权限，登记不等于授予；新增目录不自动授权。清单中含义不明的项目须核对，不猜测“全部”。')

case(2, '角色配置', ADMIN + '；协作：OA 系统',
 '管理员创建角色或调整已有角色权限，经 OA 批准并执行后生效。',
 '需要建立角色，或调整角色的系统进入权限及功能操作集合。',
 '管理员已登录并具有目标公司、组织和系统的配置申请资格。', '''
管理员选择归属公司、组织和系统，新建角色或选择已有角色。|角色名称、全局唯一编码、组织归属、业务系统
系统展示该系统可配置权限及角色已有配置。|系统进入权限、菜单及操作、当前配置
管理员勾选系统进入权限或功能操作，并填写申请原因。|功能权限集合、申请原因
系统展示配置差异和影响，供管理员核对。|新增操作、移除操作、影响摘要
管理员确认提交角色配置申请。|确认后的完整配置方案
系统受理申请并提交 OA，显示待审批。|申请编号、申请内容、待审批状态
OA 返回最终批准结果，由 UC-11 接收并执行变更。|最终审批结果、执行结果
管理员查看结果，系统显示已生效的角色配置。|角色配置、执行状态
''', '''
1a. 仅保存草稿：保留未生效配置；新角色不可分配，可稍后继续。|草稿编号、状态
3a. 越管理范围、选择无效操作、混入其他系统权限或配置为空：拒绝提交，返回修改。|不合法项、原因
5a. 无实际变更或已有未结束配置申请：不新建审批，提示现状。|现有配置或申请
7a. OA 驳回：有效配置不变；可复制原方案为新草稿，修改后重新提交。|驳回原因、原申请
7b. 方案失效或执行失败：显示未生效及原因，按 UC-11 处理。|申请状态、失败原因
''', '角色属于一个公司和一个系统，可限定任职部门，岗位不自动绑定。同范围管理员协作维护部门角色，公司级共享角色仅总管理员维护；总管理员可管理全部公司、系统。旧配置在修改审批期间继续有效；全部权限仅配置是否授予；一期无独立角色启停。')

case(3, '员工授权', ADMIN + '；协作：OA 系统',
 '为员工申请新增业务角色，审批执行成功后员工获得对应授权。',
 '员工入职、兼岗或工作需要新增角色。',
 '管理员已登录且有管理资格；目标员工有效并满足角色任职条件，角色已生效。', '''
管理员选择目标公司和待授权员工。|公司、员工统一标识、任职信息
系统展示已有授权及可分配角色。|已有角色、授权来源、有效角色
管理员明确各员工拟新增的角色并填写原因。|员工—角色—来源公司/部门配对、周期与原因
系统展示实际新增明细和权限摘要。|角色所属系统、功能权限、进入资格提示
管理员确认提交，系统受理并交由 OA 审批。|固定分配清单、申请编号、待审批状态
OA 返回最终批准结果，由 UC-11 执行角色分配。|审批结果、实际新增关系
管理员查看执行结果。|生效状态、员工新增角色
''', '''
3a. 已有相同有效关系：排除重复项；全部无变化时不提交审批。|无变化明细
5a. 存在越范围、任职不符、无效角色或重叠申请：拒绝，修正后再提交。|问题明细、现有申请
6a. OA 驳回：不增加角色；修改后以新方案重新申请。|驳回原因
6b. 角色内容变化或员工已不符合任职条件：原方案失效，重新申请。|失效原因
6c. 执行失败：整批不增加授权，按 UC-11 恢复处理。|执行状态
''', '只新增选定关系，不替换已有角色；允许兼岗和跨系统持有多个角色。功能权限来自角色配置；授权另固定来源部门及周期；仅有功能权限而无同公司、同系统进入权限时功能不可使用。失效历史不阻止重新申请。岗位变化但条件仍满足时保留；来源关闭或失去来源任职则授权失效，恢复任职后重新申请。部门角色只适用直接任职。')

case(4, '员工授权', ADMIN + '；协作：OA 系统',
 '撤销指定员工与角色的授权关系，保留其他有效来源的权限。',
 '员工职责变化或不再需要某项角色授权。',
 '管理员已登录并具备目标授权来源的撤权资格。', '''
管理员查询目标员工可管理的角色授权。|员工、公司、系统、授权来源
系统展示范围内仍有效的可撤销关系，失效历史只读。|角色关联、状态、来源组织
管理员选择撤销关系并填写原因。|明确的撤销清单、撤销原因
系统展示撤销明细和可见范围内的影响。|角色及权限摘要、重合权限提示
管理员确认提交，系统受理并交由 OA 审批。|固定撤销清单、申请编号
OA 返回最终批准结果，由 UC-11 执行撤销。|最终审批结果、执行结果
管理员查看结果，后续权限按剩余有效角色计算。|撤销状态、剩余授权
''', '''
3a. 关联已撤销：排除无变化项；全部无变化时不发起审批。|当前关系状态
5a. 超管理范围或目标关系已变化：拒绝提交，重新查询。|拒绝原因
6a. OA 驳回：本次申请不改变授权；可修改后重新申请。|驳回结果
6b. 旧方案已不适用：不撤销后续重新授予的新关系。|失效原因
6c. 执行失败：整批不执行撤销，按 UC-11 处理。|失败状态
''', '撤销关联不删除角色、不停用员工、不影响其他员工；重合权限仍由剩余角色提供。员工来源已关闭或失效时，关系作为只读历史／无变化项；旧回调不得影响重新授予。一般撤销提交不改变授权；离岗关闭原部门来源立即生效并审计，不经 OA，其他来源保留。')

case(5, '对外权限服务', '业务系统后端',
 '查询员工在当前公司、调用方所属系统的进入资格、可见菜单及允许的功能操作。',
 '业务系统需要取得员工当前权限，例如展示功能入口时。',
 '调用方已登记；业务系统自行认证员工，并可信传递统一员工标识。', '''
业务系统提交员工与当前公司权限查询请求。|服务身份、员工统一标识、当前公司、系统标识
权限中心确认调用身份、公司和系统，检查员工有效性及任职。|身份和系统匹配结果、公司及员工任职
权限中心确定员工在当前公司、当前系统的进入资格及有效权限。|进入资格、未失效的已生效授权
权限中心返回进入资格、可见菜单及允许的功能操作集合。|进入资格、菜单集合、功能操作集合
业务系统接收结果，用于本系统入口和功能展示。|权限结果、菜单与本地页面的对应关系
''', '''
2a. 参数不合法、身份无效、跨系统或系统停用：拒绝查询，返回错误。|错误原因
3a. 员工不存在、停用、无当前公司有效任职或无该公司进入资格：返回无进入资格，菜单和操作为空。|进入资格为否、空集合
3b. 有进入资格但无功能授权：返回有进入资格，菜单和操作为空。|进入资格为是、空集合
4a. 查询服务故障或授权数据异常：返回错误，不伪装为空权限。|服务错误
''', '同公司、同系统有效角色权限取并集；相同功能操作去重，不同操作互不推导。菜单须有对应查看／进入页面权限，祖先可仅作导航。编辑权限不自动推导查看权限。业务系统负责后续功能访问拦截；待审批方案不参与计算。')

case(6, '对外权限服务', '业务系统后端',
 '查询员工是否拥有指定系统进入权限或功能操作权限。',
 '业务系统准备让员工进入系统或执行某项受保护操作。',
 '调用方已登记，员工身份由业务系统认证并可信传递。', '''
业务系统提交员工、当前公司及待检查权限。|服务身份、员工统一标识、当前公司、系统、权限编码
权限中心确认调用身份、公司及系统归属、员工状态和任职。|调用资格、公司、员工状态与任职
权限中心判定指定权限是否有效且已获授；功能操作同时要求系统进入资格。|权限类型、有效授权、进入资格
权限中心返回是否拥有该功能权限。|有无权限；系统进入权限与功能操作均为布尔结果
业务系统据结果执行访问限制及自身业务校验。|拒绝／允许对应功能、后续业务校验
''', '''
2a. 参数错误、身份无效、跨系统或系统停用：返回调用错误，不提供正常鉴权结果。|错误说明
3a. 员工无效、权限未知／停用、未获授或缺少所需进入资格：返回无权限。|hasPermission=false
3b. 检查系统进入权限：直接判断其授权，不要求功能权限，返回有无权限。|进入权限结果
4a. 查询故障或数据异常：返回服务错误，业务系统不能据此放行。|服务错误
''', '可独立调用，不要求先调用 UC-05。权限中心不查询外部业务记录，不设计数据权限。按当前公司、系统计算功能授权；业务系统执行对应功能拦截及自身业务规则。')

case(7, '后台管理资格', '权限中心总管理员',
 '指定权限管理员的公司、管理部门及系统范围。',
 '任命、替换管理员或调整部门/系统范围。',
 '总管理员有效；公司、部门、候选员工及后台账号合法。', """
总管理员选择公司和管理部门。|公司、指定部门、不跨公司边界
系统展示具体范围、任命及版本。|部门范围、系统集合、管理员名单
总管理员调整系统和名单。|部门/系统增减、任免明细
系统预览业务影响。|后续管理、待执行申请影响
总管理员填写原因并确认。|固定配置、版本、原因
系统复核并整体保存范围、任命及审计。|直接生效结果
""", """
3a. 无账号、账号停用或任职不合法：拒绝新增任命。|问题身份
3b. 重复任命、跨公司部门或陈旧版本：拒绝。|规则/冲突
5a. 明确空名单或空系统：提示影响后保存。|暂时无人或无系统
6a. 写入/审计失败：整体回滚。|旧范围保留
""", '同范围协作，指定部门不跨公司；取消旧公司人数限制。公司共享角色仅总管理员维护，部门管理员可申请分配。任命不授予业务权限，普通员工不登录。')

case(8, '授权审计', ADMIN,
 '在管理范围内查询角色配置与员工授权的历史及执行结果。',
 '管理员需要核对某次权限变更或追溯授权来源。',
 '管理员已登录并具有当前查询范围内的管理资格。', '''
管理员打开审计查询并填写条件。|时间、公司、系统、员工／角色、事件、申请编号
系统展示范围内匹配记录。|摘要列表、结果数量、分页
管理员选择一条记录查看详情。|记录标识
系统展示变更内容及关联审批、执行情况。|操作者、时间、变更前后、审批和执行状态
管理员核对记录，或调整条件继续查询。|查询条件、核对结果
''', '''
1a. 条件不合法：提示修正，重新查询。|问题字段
2a. 无匹配记录：显示空列表，不作为服务故障。|零条匹配结果
2b／4a. 资格失效或超范围：拒绝访问，不泄露目标内容。|不可访问提示
4b. 员工或角色已改名、员工停用或授权失效：在授权范围内仍展示对应历史。|历史身份及名称
2c／4c. 查询服务故障：提示错误，不显示成没有日志。|服务错误
''', '只读，不修改、删除或重放历史。批准与执行成功分别展示；历史不等于当前权限。权限管理员查获准公司、来源指定部门及系统内记录；全局目录和任免日志仅总管理员查看。')

case(9, '后台访问', ADMIN,
 '管理员登录和退出权限中心后台；配套支持账号建立及密码维护。',
 '管理员访问登录页面，或已登录管理员选择退出。',
 '登录前后台账号已建立，账号、关联员工（若有）及管理资格有效。', '''
管理员输入账号和密码并提交登录。|本平台登录名、密码
系统验证账号及当前管理资格。|账号／员工状态、管理资格
系统显示登录成功及当前公司、部门和系统范围。|管理员身份、公司和系统范围
管理员进入获准的管理功能。|可用管理功能
管理员结束使用并选择退出。|当前登录状态
系统结束本次登录，返回登录页面。|退出结果
''', '''
2a. 账号、密码或管理资格无效：统一提示登录失败，不进入后台。|失败提示
3a. 首次／重置后使用临时密码：只允许改密或退出；改密成功后重新登录。|原密码、新密码、确认值
4a. 管理资格取消或范围缩小：后续操作按最新资格处理。|当前有效范围
4b. 本人改密：验证并保存新密码，使全部旧登录失效，重新登录。|原密码、新密码
6a. 已退出或登录过期：维持已退出状态，不恢复旧登录。|退出结果
''', '仅本平台账号，不负责外部系统登录；无自由注册，不经 OA。配套：总管理员为有效员工创建唯一账号及临时密码，再由 UC-07 任命；可重置权限管理员密码并使旧登录失效。账号创建／密码重置不自动授予管理或业务权限。员工停用使任命失效；恢复后须重新任命，旧登录不能恢复资格。服务故障不作为密码错误。')

case(10, '基础组织与员工资料', '权限中心总管理员；数据来源：HR 系统（一期文件／模拟数据）',
 '导入或更新公司、部门、岗位、员工及任职资料，供授权管理使用。',
 '首次初始化，或收到员工、组织和任职的更新资料。',
 '总管理员已登录，已取得来源明确的资料及字段映射。', '''
总管理员选择来源文件、数据类型和工作表。|数据来源、文件、组织／员工／任职类型
总管理员核对字段与组织映射，发起预检。|唯一标识、字段映射、组织对应关系
系统展示问题、计划变更及影响。|问题行、新增／更新、停用和任职变化
总管理员确认预检通过的批次。|确定的导入内容、影响确认
系统导入资料并记录结果。|批次、实际新增／更新结果
总管理员查看执行报告。|成功／失败、数量及原因
''', '''
3a. 格式错误、身份或组织映射有歧义：修正后重新预检。|问题行、字段说明
3b. 同员工多项合法任职：保留兼岗；完全重复资料不重复新增。|任职列表、重复提示
3c. 已有角色、授权或历史引用的指定部门跨公司迁移：阻止本次变更。|受影响部门、原因
4a. 预检后内容或目标发生变化：重新预检确认。|变化提示
5a. 相同批次已执行：返回原结果；执行失败则整批不生效。|批次结果或失败原因
''', '不经 OA，不自动建后台账号、任命、分角色或授予进入权限；漏行不等于离职。全局停用使全部业务授权及权限管理员任命失效；任职条件丧失只使相关授权／任命失效；结束最后任职后同批再就职不复权。历史保留，恢复不自动授予，分别重新走 UC-03／07。提供明确人员变更模板、内部稳定 ID 及来源映射。一期不要求真实 HR 接口。')

case(11, '外部审批结果接入', 'OA 系统',
 '接收最终审批结果，并按已批准的原申请执行角色配置、分配或撤销。',
 'OA 向权限中心交付某项申请的最终审批结果。',
 '本地已存在正式提交的申请，并已与外部审批关联。', '''
OA 提交最终批准结果及对应申请信息。|OA 服务身份、申请与审批编号、最终结果
权限中心核对结果来源、申请及批准内容。|固定申请、内容匹配情况
权限中心记录批准结果，并确认原方案仍符合执行条件。|审批状态、原资格依据、当前范围和目标条件
权限中心自动执行获批的角色配置、分配或撤销。|批准的完整变更清单
权限中心记录实际生效结果及审计信息。|执行状态、实际变更、执行时间
OA 获得处理状态，管理员可查询申请结果。|接收结果、执行状态
''', '''
2a. 来源不可信、内容不符或非最终结果：拒绝执行。|拒绝原因
2b. 重复已成功通知：返回原结果，不重复授权；冲突最终结果拒绝。|已有处理结果
3a. OA 最终驳回：记录驳回，不执行权限变更，结束本次处理。|驳回结果及原因
3b. 原方案范围、目标条件、权限有效状态或版本已不合法（含停用后恢复）：标记失效，需重新申请。|失效原因
4a. 暂时执行失败：整批不生效，保留批准依据；条件仍合法时自动重试。|失败／待恢复状态
''', '仅承接 UC-02～04，不实现 OA 内部审批。原申请人离任或公司暂时无权限管理员不单独阻止执行；总管理员原申请按全局资格，权限管理员原申请按公司、部门及系统范围复核。已批准方案不提供人为取消入口。批准不等于已生效。')

CASES[8]['other'] += ' 总管理员另可创建、启停后台账号和重置密码；账号启停不改变员工业务权限，旧会话不复活。'
CASES[9]['other'] += ' 总管理员可在员工页直接全局停用/恢复，恢复不复权；部门管理员仅按 UC-04 关闭本部门来源；不设计组织启停。'

FONT_PATH = 'C:/Windows/Fonts/msyh.ttc'
FONT = ImageFont.truetype(FONT_PATH, 17)
SMALL = ImageFont.truetype(FONT_PATH, 14)
BOLD = ImageFont.truetype('C:/Windows/Fonts/msyhbd.ttc', 17)
WIDTH, LEFT, COL = 1100, 28, 680
TABLE = WIDTH - LEFT * 2

def wrap(text, width):
    lines, line = [], ''
    for c in text:
        if c == '\n':
            lines.append(line); line = ''; continue
        if FONT.getlength(line + c) > width and line:
            lines.append(line); line = c
        else:
            line += c
    lines.append(line)
    return lines

def rows(c):
    result = [([f"用例编号：{c['id']}", f"用例名称：{c['name']}"], 'meta'),
        (['级别：' + ('子功能（支撑外部审批执行）' if c['id'] == 'UC-11' else '用户目标')], 'level')]
    for key, label in [('package','包'), ('actors','参与者'), ('desc','描述'), ('trigger','触发事件'), ('pre','前置条件')]:
        result.append(([label + '：' + c[key]], 'normal'))
    result.append((['主成功场景', '信息'], 'header'))
    result.extend(([f'{i}. {a}', b], 'normal') for i,(a,b) in enumerate(c['main'],1))
    result.append((['扩展场景（编号对应主成功场景步骤）', '信息'], 'header'))
    result.extend(([a,b], 'normal') for a,b in c['extensions'])
    result.append((['其他：' + c['other']], 'normal'))
    return result

def generate():
    mx = ET.Element('mxfile', host='app.diagrams.net', agent='OOAD use case tables', version='24.7.17')
    md = ['# 课程格式用例说明表\n\nV1.2 · 2026-10-15。依据 usecases-v1 当前 UC-01～11 精简，不替代详细规则。\n']
    cards = []
    for c in CASES:
        assert len(c['main']) <= 9
        for a,_ in c['extensions']:
            refs = re.findall(r'(\d+)[a-z]', a.split('.',1)[0])
            assert refs and all(1 <= int(n) <= len(c['main']) for n in refs)
        layout, y = [], 70
        for cells, kind in rows(c):
            widths = [TABLE] if len(cells)==1 else ([TABLE/2, TABLE/2] if kind=='meta' else [COL,TABLE-COL])
            wrapped = [wrap(t, w-24) for t,w in zip(cells,widths)]
            h = max(40, max(map(len,wrapped))*26+16)
            layout.append((cells,kind,widths,wrapped,y,h)); y += h
        height = y + 48
        page = ET.SubElement(mx,'diagram',id=c['id'],name=f"{c['id']} {c['name']}")
        model = ET.SubElement(page,'mxGraphModel',dx='1200',dy='1600',grid='1',gridSize='10',page='1',pageScale='1',pageWidth=str(WIDTH),pageHeight=str(height),math='0',shadow='0')
        root = ET.SubElement(model,'root')
        ET.SubElement(root,'mxCell',id='0'); ET.SubElement(root,'mxCell',id='1',parent='0')
        def cell(id,value,x,y,w,h,fill='#ffffff',bold=False,border=True,size=17):
            style=f'rounded=0;whiteSpace=wrap;html=0;fontFamily=Microsoft YaHei;fontSize={size};fontColor=#111111;align=left;verticalAlign=middle;spacingLeft=12;spacingRight=12;spacingTop=8;spacingBottom=8;fillColor={fill};strokeColor={"#333333" if border else "none"};fontStyle={1 if bold else 0};'
            el=ET.SubElement(root,'mxCell',id=id,value=value,style=style,vertex='1',parent='1')
            ET.SubElement(el,'mxGeometry',x=str(x),y=str(y),width=str(w),height=str(h),attrib={'as':'geometry'})
        title='集中权限管理系统 · 用例说明'
        cell('title',title,LEFT,15,TABLE,42,bold=True,border=False,size=23)
        im=Image.new('RGB',(WIDTH,height),'white'); draw=ImageDraw.Draw(im)
        draw.text((LEFT+12,22),title,font=ImageFont.truetype('C:/Windows/Fonts/msyhbd.ttc',23),fill='#111111')
        for i,(cells,kind,widths,wrapped,yy,h) in enumerate(layout):
            x=LEFT; fill='#d8e3f3' if kind=='level' else '#e2e2e2' if kind=='header' else '#ffffff'
            for j,(value,w,lines) in enumerate(zip(cells,widths,wrapped)):
                cell(f'r{i}c{j}',value,x,yy,w,h,fill,kind in ('header','level'))
                draw.rectangle((x,yy,x+w,yy+h),fill=fill,outline='#333333',width=1)
                top=yy+(h-len(lines)*26)/2
                for k,line in enumerate(lines): draw.text((x+12,top+k*26),line,font=FONT,fill='#111111')
                x+=w
        footer=f"{c['id']} · 课程简表 V1.2 · 2026-10-15 · 详细规则见 usecases-v1 对应文档"
        cell('footer',footer,LEFT,y+7,TABLE,30,border=False,size=13)
        draw.text((LEFT+12,y+12),footer,font=SMALL,fill='#555555')
        if GENERATE_PREVIEWS: im.save(OUT/f"{c['id']}.png")
        md.append(f"\n## {c['id']} {c['name']}\n\n详细依据：[原用例](../usecases-v1/{c['source']})\n\n| 项目 | 内容 |\n| --- | --- |")
        md.append('| 级别 | ' + ('子功能（支撑外部审批执行）' if c['id']=='UC-11' else '用户目标') + ' |')
        for key,label in [('package','包'),('actors','参与者'),('desc','描述'),('trigger','触发事件'),('pre','前置条件')]:md.append(f"| {label} | {c[key]} |")
        md.append('\n| 主成功场景 | 信息 |\n| --- | --- |')
        md.extend(f'| {i}. {a} | {b} |' for i,(a,b) in enumerate(c['main'],1))
        md.append('\n| 扩展场景 | 信息 |\n| --- | --- |')
        md.extend(f'| {a} | {b} |' for a,b in c['extensions'])
        md.append('\n其他：'+c['other']+'\n')
        cards.append(f'<section id="{c["id"]}"><h2>{c["id"]} {html.escape(c["name"])}</h2><img src="{c["id"]}.png" alt="{html.escape(c["name"])}用例说明表"></section>')
    ET.indent(mx)
    ET.ElementTree(mx).write(OUT/'权限管理系统_课程格式用例说明.drawio',encoding='utf-8',xml_declaration=True)
    (OUT/'课程格式用例说明.md').write_text('\n'.join(md)+'\n',encoding='utf-8')
    nav=' · '.join(f'<a href="#{c["id"]}">{c["id"]}</a>' for c in CASES)
    (OUT/'预览.html').write_text('<!doctype html><html lang="zh-CN"><meta charset="utf-8"><title>课程格式用例说明</title><style>body{font-family:"Microsoft YaHei",sans-serif;margin:24px;background:#f2f3f5;color:#222}nav{position:sticky;top:0;background:white;padding:16px}section{max-width:1100px;margin:28px auto}img{width:100%;background:white}a{color:#245799}p{text-align:center}@media print{nav,h1,p,h2{display:none}body{margin:0;background:white}section{break-after:page;margin:0}img{max-height:270mm;width:auto;max-width:100%}}</style><h1>集中权限管理系统 · 课程格式用例说明</h1><p>11个用例；图片用于预览，修改请打开同目录 draw.io 文件。</p><nav>'+nav+'</nav>'+''.join(cards)+'</html>',encoding='utf-8')
    print(f'Generated {len(CASES)} editable pages, Markdown and HTML; PNG previews: {"exported" if GENERATE_PREVIEWS else "preserved"}.')
    for c in CASES: print(c['id'],len(c['main']),'main steps')

if __name__ == '__main__':
    generate()

# Keep auxiliary preview versions explicit even when invoked directly.
import sys as _preview_sys
_preview_sys.path.insert(0, str(OUT.parent))
from preview_support import annotate_indexes as _annotate_indexes
_annotate_indexes()
