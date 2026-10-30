// Hàm sinh chuỗi chuẩn VietQR EMVCo (Napas 247)
function crc16Ccitt(str) {
  let crc = 0xFFFF;
  for (let i = 0; i < str.length; i++) {
    let c = str.charCodeAt(i);
    crc ^= (c << 8);
    for (let j = 0; j < 8; j++) {
      if ((crc & 0x8000) !== 0) {
        crc = ((crc << 1) ^ 0x1021) & 0xFFFF;
      } else {
        crc = (crc << 1) & 0xFFFF;
      }
    }
  }
  return crc.toString(16).toUpperCase().padStart(4, '0');
}

function tlv(tag, val) {
  const strVal = String(val);
  const len = String(strVal.length).padStart(2, '0');
  return `${tag}${len}${strVal}`;
}

export function generateVietQrEmvco({ bankBin, accountNo, amount, memo }) {
  // 1. Beneficiary info (Tag 01 của Tag 38)
  const acquirerSub = tlv('00', bankBin);
  const accountSub = tlv('01', accountNo);
  const beneficiarySub = tlv('01', acquirerSub + accountSub);
  const serviceCodeSub = tlv('02', 'QRIBFTTA'); // Dịch vụ chuyển nhanh Napas 247

  // Tag 38: Napas VietQR info
  const napasGuid = tlv('00', 'A000000727');
  const tag38Value = napasGuid + beneficiarySub + serviceCodeSub;
  const tag38 = tlv('38', tag38Value);

  // Tag 62: Additional Data Field (Nội dung chuyển khoản)
  let tag62 = '';
  if (memo) {
    const memoSub = tlv('08', memo);
    tag62 = tlv('62', memoSub);
  }

  // Ghép các trường chuẩn EMVCo
  let raw = tlv('00', '01') + // Format
            tlv('01', '12') + // 12: Dynamic QR (có số tiền), 11: Static QR
            tag38 +
            tlv('53', '704'); // VND

  if (amount && Number(amount) > 0) {
    raw += tlv('54', String(Math.floor(amount)));
  }

  raw += tlv('58', 'VN'); // Country Code
  raw += tag62;

  // Tag 63: Checksum CRC16
  const toCrc = raw + '6304';
  const crc = crc16Ccitt(toCrc);
  return toCrc + crc;
}

const emvcoString = generateVietQrEmvco({
  bankBin: '970422', // MB Bank
  accountNo: '0696961698888',
  amount: 250000,
  memo: 'SECURE 255297'
});

console.log('Generated EMVCo VietQR string:');
console.log(emvcoString);

