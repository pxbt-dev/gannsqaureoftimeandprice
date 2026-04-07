// ── Gann Square of Time & Price — Frontend ─────────────────────
(function () {
    'use strict';

    // ── DOM refs ────────────────────────────────────────────────
    const $ = id => document.getElementById(id);
    const symbolInput = $('symbolInput');
    const intervalSelect = $('intervalSelect');
    const startDateInput = $('startDateInput');
    const analyzeBtn = $('analyzeBtn');
    const loadingOverlay = $('loadingOverlay');
    const mainContent = $('mainContent');
    const emptyState = $('emptyState');

    let analysisData = null;

    // ── Init ────────────────────────────────────────────────────
    mainContent.classList.add('hidden');
    analyzeBtn.addEventListener('click', runAnalysis);
    symbolInput.addEventListener('keydown', e => { if (e.key === 'Enter') runAnalysis(); });

    // Symbol chip click handlers
    document.querySelectorAll('.symbol-chip').forEach(chip => {
        chip.addEventListener('click', () => {
            symbolInput.value = chip.dataset.symbol;
            symbolInput.focus();
            runAnalysis();
        });
    });

    // ── Fetch & Render ──────────────────────────────────────────
    async function runAnalysis() {
        const symbol = symbolInput.value.trim().toUpperCase() || 'BTC';
        const interval = intervalSelect.value;
        const startDate = startDateInput.value || '2019-01-01';

        analyzeBtn.disabled = true;
        loadingOverlay.classList.remove('hidden');

        try {
            const resp = await fetch(`/api/gann?symbol=${symbol}&interval=${interval}&startDate=${startDate}`);
            if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
            analysisData = await resp.json();

            if (analysisData.error) {
                alert('Error: ' + analysisData.error);
                return;
            }

            emptyState.classList.add('hidden');
            mainContent.classList.remove('hidden');

            renderSummary(analysisData);
            renderChart(analysisData);
            renderTimeProjections(analysisData.timeProjections);
            renderPriceProjections(analysisData.priceProjections);
            renderAngleProjections(analysisData.angleProjections);
            renderSquareOfNine(analysisData.squareOfNine);
            renderGrid(analysisData.grid);

        } catch (err) {
            alert('Failed to fetch analysis: ' + err.message);
        } finally {
            analyzeBtn.disabled = false;
            loadingOverlay.classList.add('hidden');
        }
    }

    // ── Summary Cards ───────────────────────────────────────────
    function renderSummary(data) {
        $('valLow').textContent = formatPrice(data.low.price);
        $('subLow').textContent = data.low.date;
        $('valHigh').textContent = formatPrice(data.high.price);
        $('subHigh').textContent = data.high.date;
        $('valLatest').textContent = formatPrice(data.latest.price);
        $('subLatest').textContent = data.latest.date;

        const range = data.high.price - data.low.price;
        $('valRange').textContent = formatPrice(range);
        const pct = ((range / data.low.price) * 100).toFixed(1);
        $('subRange').textContent = `${pct}% of low`;
    }

    // ── Price Chart (Canvas) ────────────────────────────────────
    function renderChart(data) {
        const canvas = $('priceChart');
        const rect = canvas.parentElement.getBoundingClientRect();
        const dpr = window.devicePixelRatio || 1;
        canvas.width = rect.width * dpr;
        canvas.height = rect.height * dpr;
        const ctx = canvas.getContext('2d');
        ctx.scale(dpr, dpr);
        const W = rect.width;
        const H = rect.height;

        const klines = data.klines;
        if (!klines || klines.length === 0) return;

        const pad = { top: 24, right: 110, bottom: 44, left: 14 };
        const chartW = W - pad.left - pad.right;
        const chartH = H - pad.top - pad.bottom;

        // Price bounds from candle data only
        let minP = Infinity, maxP = -Infinity;
        for (const k of klines) {
            if (k.low < minP) minP = k.low;
            if (k.high > maxP) maxP = k.high;
        }

        const priceRange = maxP - minP;
        minP -= priceRange * 0.03;
        maxP += priceRange * 0.03;

        const xScale = chartW / klines.length;
        const toX = i => pad.left + i * xScale + xScale / 2;
        const toY = p => pad.top + (maxP - p) / (maxP - minP) * chartH;

        // Background
        ctx.fillStyle = 'rgba(26, 29, 46, 0.5)';
        ctx.fillRect(0, 0, W, H);

        // Grid lines + price labels
        ctx.lineWidth = 1;
        const gridLines = 8;
        for (let i = 0; i <= gridLines; i++) {
            const y = pad.top + (chartH / gridLines) * i;
            const price = maxP - ((maxP - minP) / gridLines) * i;

            ctx.strokeStyle = 'rgba(255,255,255,0.05)';
            ctx.beginPath();
            ctx.moveTo(pad.left, y);
            ctx.lineTo(W - pad.right, y);
            ctx.stroke();

            // Price label on right
            ctx.fillStyle = 'rgba(168, 173, 198, 0.85)';
            ctx.font = '11px "JetBrains Mono", monospace';
            ctx.textAlign = 'left';
            ctx.fillText(formatPriceAxis(price), W - pad.right + 10, y + 4);
        }

        // Date labels
        const labelInterval = Math.max(1, Math.floor(klines.length / 10));
        ctx.textAlign = 'center';
        for (let i = 0; i < klines.length; i += labelInterval) {
            ctx.fillStyle = 'rgba(168, 173, 198, 0.6)';
            ctx.font = '10px "JetBrains Mono", monospace';
            ctx.fillText(klines[i].date.substring(0, 10), toX(i), H - pad.bottom + 18);
        }

        // Candlesticks
        const candleW = Math.max(1, Math.min(6, xScale * 0.65));
        for (let i = 0; i < klines.length; i++) {
            const k = klines[i];
            const isBullish = k.close >= k.open;
            const color = isBullish ? '#34d399' : '#f87171';
            const x = toX(i);

            // Wick
            ctx.strokeStyle = color;
            ctx.lineWidth = 1;
            ctx.beginPath();
            ctx.moveTo(x, toY(k.high));
            ctx.lineTo(x, toY(k.low));
            ctx.stroke();

            // Body
            const bodyTop = toY(Math.max(k.open, k.close));
            const bodyBot = toY(Math.min(k.open, k.close));
            const bodyH = Math.max(1, bodyBot - bodyTop);
            ctx.fillStyle = color;
            ctx.fillRect(x - candleW / 2, bodyTop, candleW, bodyH);
        }

        // Price projection levels — draw key levels with clearer labels
        if (data.priceProjections) {
            const colors = {
                'Gann': { line: 'rgba(192, 132, 252, 0.55)', label: 'rgba(192, 132, 252, 0.95)', bg: 'rgba(192, 132, 252, 0.12)' },
                'Fibonacci': { line: 'rgba(96, 165, 250, 0.55)', label: 'rgba(96, 165, 250, 0.95)', bg: 'rgba(96, 165, 250, 0.12)' },
                'Extension': { line: 'rgba(251, 191, 36, 0.55)', label: 'rgba(251, 191, 36, 0.95)', bg: 'rgba(251, 191, 36, 0.12)' }
            };

            // Filter to only show levels within or near the price range
            const visibleProj = data.priceProjections.filter(p => {
                const price = p.projectedPrice;
                return price >= minP && price <= maxP;
            });

            for (const proj of visibleProj) {
                const price = proj.projectedPrice;
                const y = toY(price);
                const c = colors[proj.ratioType] || colors['Gann'];

                // Dashed line
                ctx.strokeStyle = c.line;
                ctx.lineWidth = 1.2;
                ctx.setLineDash([6, 4]);
                ctx.beginPath();
                ctx.moveTo(pad.left, y);
                ctx.lineTo(W - pad.right, y);
                ctx.stroke();
                ctx.setLineDash([]);

                // Label pill on right side
                const labelText = `${formatPriceAxis(price)}  ${proj.ratioType.charAt(0)} ${proj.ratioValue.toFixed(2)}`;
                ctx.font = '10px "JetBrains Mono", monospace';
                const textW = ctx.measureText(labelText).width;

                // Background pill
                ctx.fillStyle = c.bg;
                const pillX = W - pad.right + 6;
                const pillW = textW + 10;
                const pillH = 16;
                ctx.beginPath();
                ctx.roundRect(pillX, y - pillH / 2, pillW, pillH, 3);
                ctx.fill();

                // Label text
                ctx.fillStyle = c.label;
                ctx.textAlign = 'left';
                ctx.fillText(labelText, pillX + 5, y + 3.5);
            }
        }

        // Render legend
        const legend = $('chartLegend');
        legend.innerHTML = [
            ['#34d399', 'Bullish'], ['#f87171', 'Bearish'],
            ['rgba(192,132,252,0.9)', 'Gann'], ['rgba(96,165,250,0.9)', 'Fibonacci'],
            ['rgba(251,191,36,0.9)', 'Extension']
        ].map(([c, l]) =>
            `<span class="legend-item"><span class="legend-dot" style="background:${c}"></span>${l}</span>`
        ).join('');
    }

    // ── Table Renderers ─────────────────────────────────────────
    function renderTimeProjections(projections) {
        $('timeCount').textContent = projections.length;
        const tbody = $('timeTable').querySelector('tbody');
        tbody.innerHTML = projections.map(p => `
            <tr>
                <td>${p.date || '—'}</td>
                <td>${typeTag(p.ratioType)}</td>
                <td>${p.ratioValue.toFixed(3)}</td>
                <td style="color:var(--text-secondary);font-family:Inter,sans-serif;font-size:0.72rem">${p.description}</td>
            </tr>
        `).join('');
    }

    function renderPriceProjections(projections) {
        $('priceCount').textContent = projections.length;
        const tbody = $('priceTable').querySelector('tbody');
        const latest = analysisData?.latest?.price || 0;
        tbody.innerHTML = projections.map(p => {
            const isAbove = p.projectedPrice > latest;
            return `
            <tr>
                <td class="${isAbove ? 'price-up' : 'price-down'}">${formatPrice(p.projectedPrice)}</td>
                <td>${typeTag(p.ratioType)}</td>
                <td>${p.ratioValue.toFixed(3)}</td>
                <td style="color:var(--text-secondary);font-family:Inter,sans-serif;font-size:0.72rem">${p.description}</td>
            </tr>`;
        }).join('');
    }

    function renderAngleProjections(projections) {
        $('angleCount').textContent = projections.length;
        const tbody = $('angleTable').querySelector('tbody');
        tbody.innerHTML = projections.map(p => `
            <tr>
                <td>${p.date || '—'}</td>
                <td>${formatPrice(p.projectedPrice)}</td>
                <td>${typeTag(p.ratioType)}</td>
                <td style="color:var(--text-secondary);font-family:Inter,sans-serif;font-size:0.72rem">${p.description}</td>
            </tr>
        `).join('');
    }

    function renderSquareOfNine(projections) {
        $('sq9Count').textContent = projections.length;
        const tbody = $('sq9Table').querySelector('tbody');
        const latest = analysisData?.latest?.price || 0;
        tbody.innerHTML = projections.map(p => {
            const isAbove = p.projectedPrice > latest;
            const typeClass = isAbove ? 'tag-resistance' : 'tag-support';
            return `
            <tr>
                <td class="${isAbove ? 'price-up' : 'price-down'}">${formatPrice(p.projectedPrice)}</td>
                <td><span class="tag ${typeClass}">${p.ratioType}</span></td>
                <td>${p.description.match(/[\d.]+°/)?.[0] || '—'}</td>
                <td style="color:var(--text-secondary);font-family:Inter,sans-serif;font-size:0.72rem">${p.description}</td>
            </tr>`;
        }).join('');
    }

    // ── Square of Nine Grid ─────────────────────────────────────
    function renderGrid(gridData) {
        if (!gridData || !gridData.grid) return;

        const canvas = $('gridCanvas');
        const size = gridData.size;
        const cellSize = Math.min(56, Math.floor(500 / size));
        const totalSize = cellSize * size;
        const dpr = window.devicePixelRatio || 1;
        canvas.width = totalSize * dpr;
        canvas.height = totalSize * dpr;
        canvas.style.width = totalSize + 'px';
        canvas.style.height = totalSize + 'px';
        const ctx = canvas.getContext('2d');
        ctx.scale(dpr, dpr);

        const center = Math.floor(size / 2);

        for (let row = 0; row < size; row++) {
            for (let col = 0; col < size; col++) {
                const x = col * cellSize;
                const y = row * cellSize;
                const val = gridData.grid[row][col];

                const isCenter = (row === center && col === center);
                const isCardinal = (row === center || col === center) && !isCenter;
                const isDiagonal = (Math.abs(row - center) === Math.abs(col - center)) && !isCenter;

                // Background
                if (isCenter) {
                    ctx.fillStyle = 'rgba(99, 102, 241, 0.45)';
                } else if (isCardinal) {
                    ctx.fillStyle = 'rgba(248, 113, 113, 0.2)';
                } else if (isDiagonal) {
                    ctx.fillStyle = 'rgba(52, 211, 153, 0.15)';
                } else {
                    ctx.fillStyle = 'rgba(255, 255, 255, 0.03)';
                }
                ctx.fillRect(x + 0.5, y + 0.5, cellSize - 1, cellSize - 1);

                // Border
                ctx.strokeStyle = 'rgba(255, 255, 255, 0.08)';
                ctx.lineWidth = 0.5;
                ctx.strokeRect(x + 0.5, y + 0.5, cellSize - 1, cellSize - 1);

                // Number
                ctx.fillStyle = isCenter ? '#c4b5fd' :
                    isCardinal ? 'rgba(248, 113, 113, 0.9)' :
                        isDiagonal ? 'rgba(52, 211, 153, 0.85)' :
                            'rgba(255, 255, 255, 0.45)';
                ctx.font = `${isCenter ? 'bold ' : ''}${Math.max(10, cellSize * 0.3)}px "JetBrains Mono", monospace`;
                ctx.textAlign = 'center';
                ctx.textBaseline = 'middle';
                ctx.fillText(val, x + cellSize / 2, y + cellSize / 2);
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────
    function formatPrice(n) {
        if (n == null || isNaN(n)) return '—';
        if (Math.abs(n) >= 1000) return '$' + n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
        if (Math.abs(n) >= 1) return '$' + n.toFixed(2);
        return '$' + n.toFixed(4);
    }

    function formatPriceAxis(n) {
        if (n >= 1e6) return '$' + (n / 1e6).toFixed(1) + 'M';
        if (n >= 1e3) return '$' + (n / 1e3).toFixed(1) + 'K';
        return '$' + n.toFixed(2);
    }

    function typeTag(type) {
        const cls = type === 'Gann' ? 'tag-gann' :
            type === 'Fibonacci' ? 'tag-fib' :
                type === 'Extension' ? 'tag-ext' :
                    type === 'Resistance' ? 'tag-resistance' :
                        type === 'Support' ? 'tag-support' :
                            'tag-gann';
        return `<span class="tag ${cls}">${type}</span>`;
    }

    // ── Window resize handler ───────────────────────────────────
    let resizeTimer;
    window.addEventListener('resize', () => {
        clearTimeout(resizeTimer);
        resizeTimer = setTimeout(() => {
            if (analysisData) {
                renderChart(analysisData);
                renderGrid(analysisData.grid);
            }
        }, 200);
    });
})();
