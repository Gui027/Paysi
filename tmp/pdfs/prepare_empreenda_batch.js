const fs = require('fs');
const path = require('path');
const { chromium } = require(
  process.env.TEMP + '/codex-playwright/node_modules/playwright-core'
);

const BASE_URL = 'https://tec360.eadplataforma.app';
const COURSE_ID = 30;
const DATE_START = '2026-08-10 00:00:00';
const DATE_END = '2026-09-14 00:00:00';
const WORKLOAD = '24:00';
const FINAL_AVERAGE = '10.00';
const TMP_ROOT = 'C:/Projetos/Paysi/tmp/pdfs';
const SOURCE_DIR = path.join(TMP_ROOT, 'batch_sources');
const MANIFEST = path.join(TMP_ROOT, 'empreenda_batch_manifest.json');

function normalizeEmail(value) {
  return String(value || '').trim().toLowerCase();
}

function assertOk(response, action) {
  if (response.status() < 200 || response.status() >= 300) {
    throw new Error(`${action}: HTTP ${response.status()}`);
  }
}

async function mapLimit(items, limit, fn) {
  const results = new Array(items.length);
  let cursor = 0;
  async function worker() {
    while (true) {
      const index = cursor++;
      if (index >= items.length) return;
      results[index] = await fn(items[index], index);
    }
  }
  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, worker));
  return results;
}

async function main() {
  const email = process.env.TEC_USER;
  const password = process.env.TEC_PASS;
  if (!email || !password) throw new Error('TEC_USER e TEC_PASS são obrigatórios');

  fs.mkdirSync(SOURCE_DIR, { recursive: true });

  const browser = await chromium.launch({
    headless: true,
    executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe',
  });
  const context = await browser.newContext({ ignoreHTTPSErrors: true });

  const auth = await context.request.post(`${BASE_URL}/auth`, {
    data: { email, password, hash: '', remember: true },
  });
  assertOk(auth, 'Autenticação');

  const page = await context.newPage();
  let headersPromise;
  page.on('request', (request) => {
    if (request.url().includes('/admin/enrollment/list/paginate?') && !headersPromise) {
      headersPromise = request.allHeaders();
    }
  });
  await page.goto(`${BASE_URL}/adm/courses/edit/${COURSE_ID}/enrollments`);
  await page.waitForTimeout(6000);
  if (!headersPromise) throw new Error('Não foi possível capturar o token administrativo');
  const requestHeaders = await headersPromise;
  const apiHeaders = { 'x-auth-token': requestHeaders['x-auth-token'] };

  const enrollmentResponse = await context.request.get(
    `${BASE_URL}/admin/enrollment/list/paginate?listInCourse=1&course=${COURSE_ID}&limit=100&offset=0&order=%7B%22u.name%22:%22asc%22%7D`,
    { headers: apiHeaders }
  );
  assertOk(enrollmentResponse, 'Listagem de matrículas');
  const enrollmentPayload = await enrollmentResponse.json();
  const enrollments = enrollmentPayload.data?.rows || [];
  if (enrollments.length !== 66) {
    throw new Error(`Esperadas 66 matrículas; encontradas ${enrollments.length}`);
  }

  async function listCourseCertificates() {
    const response = await context.request.get(
      `${BASE_URL}/admin/certificates?limit=500&offset=0`,
      { headers: apiHeaders }
    );
    assertOk(response, 'Listagem de certificados');
    const payload = await response.json();
    return (payload.rows || []).filter((item) =>
      String(item.title || '').includes('EMPREENDA')
    );
  }

  let certificates = await listCourseCertificates();
  let byEmail = new Map(certificates.map((item) => [normalizeEmail(item.email), item]));
  let created = 0;
  let updated = 0;
  let unchanged = 0;

  for (const enrollment of enrollments) {
    const existing = byEmail.get(normalizeEmail(enrollment.email));
    if (existing) {
      const detailResponse = await context.request.get(
        `${BASE_URL}/admin/certificates/${existing.id}`,
        { headers: apiHeaders }
      );
      assertOk(detailResponse, `Leitura do certificado de ${enrollment.name}`);
      const detail = await detailResponse.json();
      if (
        detail.dateStart === DATE_START &&
        detail.dateEnd === DATE_END &&
        detail.workload === WORKLOAD &&
        detail.finalAverage === FINAL_AVERAGE
      ) {
        unchanged += 1;
        continue;
      }
      const response = await context.request.put(
        `${BASE_URL}/admin/certificates/${existing.id}`,
        {
          headers: apiHeaders,
          data: {
            dateStart: DATE_START,
            dateEnd: DATE_END,
            workload: WORKLOAD,
            dateIssue: detail.dateIssue,
            dateExpired: detail.dateExpired,
            finalAverage: FINAL_AVERAGE,
            courseCertificateTemplate: detail.courseCertificateTemplate,
          },
          timeout: 120000,
        }
      );
      assertOk(response, `Atualização do certificado de ${enrollment.name}`);
      updated += 1;
    } else {
      const response = await context.request.post(`${BASE_URL}/admin/certificates`, {
        headers: apiHeaders,
        data: {
          user: enrollment.userId,
          course: COURSE_ID,
          dateStart: DATE_START,
          dateEnd: DATE_END,
          workload: WORKLOAD,
          finalAverage: FINAL_AVERAGE,
          sendEmail: 0,
        },
        timeout: 120000,
      });
      assertOk(response, `Emissão do certificado de ${enrollment.name}`);
      created += 1;
    }
  }

  certificates = await listCourseCertificates();
  byEmail = new Map(certificates.map((item) => [normalizeEmail(item.email), item]));

  const rows = enrollments.map((enrollment, index) => {
    const certificate = byEmail.get(normalizeEmail(enrollment.email));
    if (!certificate) throw new Error(`Certificado ausente: ${enrollment.name}`);
    return {
      sequence: index + 1,
      enrollmentId: enrollment.id,
      userId: enrollment.userId,
      name: enrollment.name,
      email: enrollment.email,
      certificateId: certificate.id,
      certificateCode: certificate.newCode || certificate.code,
      sourcePath: path.join(
        SOURCE_DIR,
        `${String(index + 1).padStart(3, '0')}-${certificate.id}.pdf`
      ),
    };
  });

  await mapLimit(rows, 5, async (row) => {
    const detailResponse = await context.request.get(
      `${BASE_URL}/admin/certificates/${row.certificateId}`,
      { headers: apiHeaders }
    );
    assertOk(detailResponse, `Detalhes de ${row.name}`);
    const detail = await detailResponse.json();
    if (
      detail.dateStart !== DATE_START ||
      detail.dateEnd !== DATE_END ||
      detail.workload !== WORKLOAD ||
      detail.finalAverage !== FINAL_AVERAGE
    ) {
      throw new Error(`Dados divergentes no certificado de ${row.name}`);
    }

    const pdfResponse = await context.request.get(
      `${BASE_URL}/admin/certificates/view/${row.certificateId}`,
      { headers: apiHeaders, timeout: 120000 }
    );
    assertOk(pdfResponse, `Download de ${row.name}`);
    const buffer = await pdfResponse.body();
    if (buffer.subarray(0, 4).toString() !== '%PDF') {
      throw new Error(`Arquivo inválido para ${row.name}`);
    }
    fs.writeFileSync(row.sourcePath, buffer);
  });

  fs.writeFileSync(
    MANIFEST,
    JSON.stringify(
      {
        courseId: COURSE_ID,
        dateStart: DATE_START,
        dateEnd: DATE_END,
        workload: WORKLOAD,
        finalAverage: FINAL_AVERAGE,
        created,
        updated,
        unchanged,
        count: rows.length,
        rows,
      },
      null,
      2
    ),
    'utf8'
  );

  console.log(JSON.stringify({ count: rows.length, created, updated, unchanged, manifest: MANIFEST }));
  await browser.close();
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
