export const mockTransit = {
  line: '2호선',
  station: '강남',
  direction: '역삼 방면',
  departures: [
    { time: '07:12', waitMin: 3, crowding: 'moderate' },
    { time: '07:16', waitMin: 7, crowding: 'low' },
    { time: '07:21', waitMin: 12, crowding: 'low' },
    { time: '07:26', waitMin: 17, crowding: 'moderate' },
    { time: '07:32', waitMin: 23, crowding: 'high' },
  ],
  alerts: [
    { message: '2호선 사당~낙성대 구간 3분 지연', severity: 'info' },
  ],
}
