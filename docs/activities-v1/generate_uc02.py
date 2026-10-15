"""Regenerate only UC-02 using the current straight-edge activity generator."""
from generate_all import OUT, cases, build, render, container, write

if __name__ == '__main__':
    flow = cases[2][0]
    mx = container()
    mx.append(render(build(2, flow)))
    write(OUT / 'UC-02_创建角色并配置权限_活动图.drawio', mx)
    print('UC-02 activity and local preview regenerated; combined file unchanged.')
