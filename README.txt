OldMarket (com.oldmarket) — Eclipse/ADT project (fixed for Java 1.6 / no lambdas)

Кодировка:
- Все файлы UTF-8. Русский язык: res/values-ru/strings.xml

ВАЖНО:
- Проект НЕ использует android-support-v4 (чтобы не было ошибок android.support).
- Уведомление скачивания сделано через старый Notification#setLatestEventInfo (совместимо с minSdk 7).

Настройки:
- Открываются долгим нажатием на логотип на главном экране.

Функции:
- Главный экран: баннер + Игры/Приложения + список + пагинация
- Поиск: SearchActivity, серверный /api/apps/search
- Фильтр: app.api <= API устройства
- Иконки: RAM LRU (простая) + диск (/cache/icons)
- Скачивание: диалог прогресса + уведомление + отмена + запуск установщика
