import { useAssetData } from '../../hooks/useMockData'
import { cn } from '../../utils/cn'
import styles from './AssetCard.module.css'

function formatPrice(price, currency) {
  if (currency === 'KRW') return price.toLocaleString('ko-KR') + '원'
  return '$' + price.toLocaleString('en-US', { minimumFractionDigits: 2 })
}

export default function AssetCard() {
  const { data } = useAssetData()

  return (
    <div className={styles.assets}>
      <ul className={styles.list}>
        {data.items.map((item) => (
          <li key={item.ticker} className={styles.item}>
            <div className={styles.info}>
              <span className={styles.name}>{item.name}</span>
              <span className={styles.ticker}>{item.ticker}</span>
            </div>
            <div className={styles.priceBlock}>
              <span className={styles.price}>
                {formatPrice(item.price, item.currency)}
              </span>
              <span
                className={cn(
                  styles.change,
                  item.changePercent >= 0 ? styles.up : styles.down
                )}
              >
                {item.changePercent >= 0 ? '+' : ''}{item.changePercent.toFixed(2)}%
              </span>
            </div>
          </li>
        ))}
      </ul>
      <div className={styles.updated}>마지막 업데이트: {data.lastUpdated}</div>
    </div>
  )
}
