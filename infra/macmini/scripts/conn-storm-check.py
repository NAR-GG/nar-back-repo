"""바깥 목적지로 TLS 연결 N개를 동시에 열어 개통 지연을 잰다 — VM 네트워크(usernet) SYN 드롭 판별용.

사용: python3 conn-storm-check.py 100 300 100   (맥 호스트와 `colima ssh -- python3 ...` 에서 각각)
DNS 는 1회만 풀고 IP 로 붙는다(DNS 몰림을 섞지 않으려고). 리눅스면 TCPSynRetrans 증가분도 찍는다.
2026-10-26 기준선: 맥 호스트 100개 0.34초 / VM 안 100개 11~36초(SynRetrans +274~450), 300개 136초.
"""
import asyncio, ssl, socket, sys, time, platform

HOST = "fcm.googleapis.com"

def syn_retrans():
    try:
        lines = open("/proc/net/netstat").read().splitlines()
        for i in range(0, len(lines), 2):
            if lines[i].startswith("TcpExt:"):
                k, v = lines[i].split()[1:], lines[i + 1].split()[1:]
                d = dict(zip(k, map(int, v)))
                return d.get("TCPSynRetrans"), d.get("TCPTimeouts")
    except Exception:
        return None
    return None

async def one(ip, ctx):
    t = time.monotonic()
    try:
        r, w = await asyncio.wait_for(
            asyncio.open_connection(ip, 443, ssl=ctx, server_hostname=HOST), 180)
        return time.monotonic() - t, None, w
    except Exception as e:
        return time.monotonic() - t, type(e).__name__, None

async def main(n):
    ip = socket.getaddrinfo(HOST, 443, socket.AF_INET, socket.SOCK_STREAM)[0][4][0]
    ctx = ssl.create_default_context()
    ctx.set_alpn_protocols(["h2"])
    before = syn_retrans()
    t0 = time.monotonic()
    res = await asyncio.gather(*[one(ip, ctx) for _ in range(n)])
    total = time.monotonic() - t0
    after = syn_retrans()
    for _, _, w in res:
        if w: w.close()
    ds = sorted(r[0] for r in res)
    errs = [r[1] for r in res if r[1]]
    q = lambda p: ds[min(len(ds) - 1, int(p * len(ds)))]
    slow = sum(1 for d in ds if d > 0.9)
    etypes = (" " + str(sorted(set(errs)))) if errs else ""
    syn = f" synRetrans+{after[0]-before[0]} timeouts+{after[1]-before[1]}" if before and after else ""
    print(f"n={n} total={total:.2f}s p50={q(.5):.2f} p90={q(.9):.2f} max={ds[-1]:.2f} over1s={slow} errors={len(errs)}{etypes}{syn}", flush=True)

for n in map(int, sys.argv[1:]):
    asyncio.run(main(n))
    time.sleep(3)
