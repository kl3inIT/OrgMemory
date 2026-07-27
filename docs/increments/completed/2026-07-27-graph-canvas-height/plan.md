# Graph Canvas Height Plan

- [x] Reproduce the production error and identify the broken height chain.
- [x] Confirm React Sigma requires a non-zero explicitly bounded container.
- [x] Make `PageLayout.Canvas` a flex container.
- [x] Render fullscreen through the shared graph icon-control primitive.
- [x] Pass frontend static, unit, and production build gates.
- [x] Verify a populated graph in a real browser with the equivalent CSS rule.
- [x] Merge, deploy, and verify the production route.

Production evidence: PR #75; commit `4fa215c`; CI `30237427612`; image build
`30237507156`; deployment `30237618292`; browser verification showed 92
entities, 129 relations, zero console errors, and aligned 32-pixel controls.
