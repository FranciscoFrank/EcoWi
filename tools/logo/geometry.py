"""EcoWi neon mark — exact geometry reconstructed from the user's reference (289x207 px space).

The mark is one neon tube tracing the outline of a thick "W" (four strokes, slopes ±S),
with two breaks on the right inner stroke through which a lightning bolt runs; the bolt's
tail narrows into the tube. All numbers below were measured/fitted on the reference.
"""
import math

S = 0.355            # |dx/dy| of every stroke (fitted 0.350–0.366)
D = 23.2             # horizontal spacing of the two tube lines of one stroke
AXIS = 145.25        # vertical symmetry axis
TUBE = 7.0           # tube thickness
CORE = 3.4           # bright core thickness

# stroke centrelines at y=100 (symmetric pairs about AXIS)
C1, C2 = AXIS - 85.5, AXIS - 24.3
C3, C4 = AXIS + 24.3, AXIS + 85.5


class Line:
    """x = c + k*(y-100)"""
    def __init__(self, c, k):
        self.c, self.k = c, k

    def x(self, y):
        return self.c + self.k * (y - 100)

    def at(self, y):
        return (self.x(y), y)

    def dir(self):  # unit direction pointing down (+y)
        n = math.hypot(self.k, 1)
        return (self.k / n, 1 / n)


L1 = Line(C1 - D / 2, S)      # left stroke, outer
L2 = Line(C1 + D / 2, S)      # left stroke, inner
L3 = Line(C2 - D / 2, -S)     # 2nd stroke, upper-left
L4 = Line(C2 + D / 2, -S)     # 2nd stroke, lower-right
L5 = Line(C3 - D / 2, S)      # 3rd stroke, lower-left
L6 = Line(C3 + D / 2, S)      # 3rd stroke, upper-right (broken by the bolt)
L7 = Line(C4 - D / 2, -S)     # right stroke, inner
L8 = Line(C4 + D / 2, -S)     # right stroke, outer
TAIL = Line(243.83 - 0.5925 * 100, -0.5925)   # bolt tail centreline (fitted)

# measured extreme points of each bend (y of the topmost / bottommost tube centre)
CAP_TOP = 23.0        # top of the left/right end caps
PEAK_TOP = 23.5       # top of the middle peak
VALLEY_IN = 135.0     # bottom of the two inner valleys
VALLEY_OUT = 180.0    # bottom of the two outer U-bends
HUMP = 101.9          # top of the middle "crotch" from below
V_RADIUS = 3.0        # small bend where the tube turns up into the bolt tail

# gaps (y of tube end points)
L6_UPPER_END = 82.0
L6_HOOK_START = 118.5
L5_LOWER_START = 141.7
TAIL_END = 97.0

# bolt polygon (tail edges end where the tail is as wide as the tube)
BOLT_TIP = (211.5, 21.5)
BOLT_LEFT = (181.5, 65.5)
BOLT_NOTCH = (198.5, 66.5)
BOLT_RIGHT = (218.5, 56.0)
BOLT_KNEE = (203.5, 55.0)
BOLT_TAIL_Y = 103.0


def intersect(a, b):
    # a.c + a.k*(y-100) = b.c + b.k*(y-100)
    y = 100 + (b.c - a.c) / (a.k - b.k)
    return (a.x(y), y)


def fillet_radius_for_extreme(a, b, extreme_y, up):
    """Radius of the arc tangent to a and b whose top (up=True) / bottom point is extreme_y."""
    vx, vy = intersect(a, b)
    half = math.atan(abs(a.k))  # both lines make the same angle with the vertical
    # centre lies on the vertical through V (symmetric bend) at distance R/sin(half)
    # up=True: arc's top is extreme, centre below V; else centre above V
    if up:
        # centre y = vy + R/sin ; top = centre - R
        return (extreme_y - vy) / (1 / math.sin(half) - 1)
    return (vy - extreme_y) / (1 / math.sin(half) - 1)


def sub(p, q): return (p[0] - q[0], p[1] - q[1])
def add(p, q): return (p[0] + q[0], p[1] + q[1])
def mul(p, s): return (p[0] * s, p[1] * s)
def norm(p):
    n = math.hypot(*p); return (p[0] / n, p[1] / n)
def cross(u, v): return u[0] * v[1] - u[1] * v[0]


def fillet(prev_pt, a, b, next_pt, r):
    """Tangent points + sweep for a bend from line a (travelling from prev_pt) to line b (towards next_pt)."""
    V = intersect(a, b)
    u = norm(sub(V, prev_pt))
    v = norm(sub(next_pt, V))
    ang = math.acos(max(-1, min(1, -(u[0] * v[0] + u[1] * v[1]))))  # interior angle
    t = r / math.tan(ang / 2)
    t1, t2 = sub(V, mul(u, t)), add(V, mul(v, t))
    sweep = 1 if cross(u, v) > 0 else 0
    return t1, t2, sweep


def ucap(a, b, top_y, going_up_on_a):
    """True semicircle joining two parallel slanted lines (end caps of the outer strokes):
    centre midway between the lines, tangent points along the normal, topmost point at top_y."""
    r = abs(b.c - a.c) / 2 * math.cos(math.atan(abs(a.k)))
    cy = top_y + r
    o = ((a.x(cy) + b.x(cy)) / 2, cy)
    d = a.dir()
    n = (d[1], -d[0])                      # unit normal
    t1, t2 = add(o, n), add(o, mul(n, -1))
    t1, t2 = (add(o, mul(n, r)), add(o, mul(n, -r)))
    # t1 must be the point on line a
    if abs(t1[0] - a.x(t1[1])) > abs(t2[0] - a.x(t2[1])):
        t1, t2 = t2, t1
    u = (0, -1)
    sweep = 1 if cross(u, sub(t2, t1)) > 0 else 0
    return t1, t2, r, sweep


class P:
    """Tiny path builder (absolute coords) with optional transform."""
    def __init__(self, tf):
        self.tf, self.d = tf, []

    def _p(self, pt):
        x, y = self.tf(pt)
        return f"{x:.2f},{y:.2f}"

    def M(self, pt): self.d.append("M" + self._p(pt)); return self
    def L(self, pt): self.d.append("L" + self._p(pt)); return self
    def A(self, r, sweep, pt, scale):
        rr = r * scale
        self.d.append(f"A{rr:.2f},{rr:.2f} 0 0,{sweep} " + self._p(pt)); return self
    def Z(self): self.d.append("Z"); return self
    def s(self): return " ".join(self.d)


def tube_paths(tf, scale):
    """Returns (main, hook, tail-free?) path strings for the two tube pieces."""
    r_peak = fillet_radius_for_extreme(L3, L6, PEAK_TOP, up=True)
    r_vin = fillet_radius_for_extreme(L2, L3, VALLEY_IN, up=False)
    r_vout = fillet_radius_for_extreme(L1, L4, VALLEY_OUT, up=False)
    r_hump = fillet_radius_for_extreme(L4, L5, HUMP, up=True)

    # ---- main piece: L6 (upper part) ↑ peak ↓ L3 → inner valley B ↑ L2 → cap ↓ L1 → U-bend ↑ L4 → hump ↓ L5 → V ↑ tail
    p = P(tf)
    start = L6.at(L6_UPPER_END)
    p.M(start)
    t1, t2, sw = fillet(start, L6, L3, L3.at(100), r_peak); p.L(t1).A(r_peak, sw, t2, scale)
    t1, t2, sw = fillet(L3.at(60), L3, L2, L2.at(60), r_vin); p.L(t1).A(r_vin, sw, t2, scale)
    t1, t2, r, sw = ucap(L2, L1, CAP_TOP, True); p.L(t1).A(r, sw, t2, scale)
    t1, t2, sw = fillet(L1.at(100), L1, L4, L4.at(100), r_vout); p.L(t1).A(r_vout, sw, t2, scale)
    t1, t2, sw = fillet(L4.at(140), L4, L5, L5.at(120), r_hump); p.L(t1).A(r_hump, sw, t2, scale)
    t1, t2, sw = fillet(L5.at(110), L5, TAIL, TAIL.at(TAIL_END), V_RADIUS); p.L(t1).A(V_RADIUS, sw, t2, scale)
    p.L(TAIL.at(TAIL_END))
    main = p.s()

    # ---- hook piece: L6 (hook) ↓ inner valley D ↑ L7 → cap ↓ L8 → U-bend ↑ L5 (lower part)
    q = P(tf)
    start = L6.at(L6_HOOK_START)
    q.M(start)
    t1, t2, sw = fillet(start, L6, L7, L7.at(60), r_vin); q.L(t1).A(r_vin, sw, t2, scale)
    # cap: travel up L7, down L8
    t1, t2, r, _ = ucap(L7, L8, CAP_TOP, True)
    sw = 1 if cross((0, -1), sub(t2, t1)) > 0 else 0
    q.L(t1).A(r, sw, t2, scale)
    t1, t2, sw = fillet(L8.at(100), L8, L5, L5.at(L5_LOWER_START), r_vout); q.L(t1).A(r_vout, sw, t2, scale)
    q.L(L5.at(L5_LOWER_START))
    hook = q.s()
    return main, hook, dict(peak=r_peak, valley_in=r_vin, valley_out=r_vout, hump=r_hump)


def bolt_path(tf):
    half = TUBE / 2 / math.cos(math.atan(abs(TAIL.k)))   # horizontal half-width of the tube on the tail
    tail_l = (TAIL.x(BOLT_TAIL_Y) - half, BOLT_TAIL_Y)
    tail_r = (TAIL.x(BOLT_TAIL_Y) + half, BOLT_TAIL_Y)
    p = P(tf)
    p.M(BOLT_TIP).L(BOLT_LEFT).L(BOLT_NOTCH).L(tail_l).L(tail_r).L(BOLT_RIGHT).L(BOLT_KNEE).Z()
    return p.s()


# bounding box of the mark (tube centrelines ± half tube), used for fitting into targets
BBOX = (21.5, 19.5, 268.9, 183.5)
CENTER = ((BBOX[0] + BBOX[2]) / 2, (BBOX[1] + BBOX[3]) / 2)


def fit(target_cx, target_cy, scale):
    cx, cy = CENTER
    return lambda pt: (target_cx + (pt[0] - cx) * scale, target_cy + (pt[1] - cy) * scale)
