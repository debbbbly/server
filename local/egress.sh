lk egress start  --api-key dev-livekit-api-key-for-development --api-secret dev-livekit-api-secret-for-development --url http://localhost:7880 --type room-composite room_egress.json

lk egress stop --id EG_Tdz4TDt4iTto --api-key dev-livekit-api-key-for-development --api-secret dev-livekit-api-secret-for-development --url http://localhost:7880

aws --endpoint-url https://zhrclcryabanraaadfty.storage.supabase.co/storage/v1/s3 s3 cp room_egress.json s3://egress/room_egress.json
