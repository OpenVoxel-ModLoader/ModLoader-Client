# Sound pack

Put `.wav` files here named after the event; numbered variants are picked at random:

| Event    | Files                      | When                                   |
|----------|----------------------------|----------------------------------------|
| step     | step.wav, step1.wav, ...   | walking on the ground                  |
| break    | break.wav                  | a block breaks                         |
| place    | place.wav                  | a block is placed                      |
| pickup   | pickup.wav                 | a dropped item is collected            |
| eat      | eat.wav                    | each crunch while eating               |
| pop      | pop.wav                    | food finished                          |
| hurt     | hurt.wav                   | taking damage                          |
| click    | click.wav                  | pick block (middle click)              |

Missing events fall back to synthesized sounds. Reload in game with `/music reload`.
16-bit PCM WAV only (Java Sound): `ffmpeg -i in.ogg -acodec pcm_s16le out.wav`.
