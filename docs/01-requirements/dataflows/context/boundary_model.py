"""Concise context-level data flows; each line names a data collection."""
ENTITIES={'admin':'权限中心总管理员','manager':'权限管理员','oa':'OA 系统（一期模拟）','business':'业务系统后端'}
FLOWS=[
    dict(id='F01',source='admin',target='system',name='登录、配置、授权、人事及查询资料',data=[],usecases=['UC-01','UC-02','UC-03','UC-04','UC-07','UC-08','UC-09','UC-10']),
    dict(id='F02',source='system',target='admin',name='身份、管理结果及审计资料',data=[],usecases=['UC-01','UC-02','UC-03','UC-04','UC-07','UC-08','UC-09','UC-10']),
    dict(id='F03',source='manager',target='system',name='登录及范围内管理、查询资料',data=[],usecases=['UC-02','UC-03','UC-04','UC-08','UC-09']),
    dict(id='F04',source='system',target='manager',name='身份、范围内资料及办理结果',data=[],usecases=['UC-02','UC-03','UC-04','UC-08','UC-09']),
    dict(id='F05',source='system',target='oa',name='权限变更申请、结果接收反馈',data=[],usecases=['UC-02','UC-03','UC-04','UC-11']),
    dict(id='F06',source='oa',target='system',name='审批关联、批准／驳回结果',data=[],usecases=['UC-11']),
    dict(id='F07',source='business',target='system',name='员工、公司及权限查询条件',data=[],usecases=['UC-05','UC-06']),
    dict(id='F08',source='system',target='business',name='有效权限集合或校验结果',data=[],usecases=['UC-05','UC-06']),
]
