final jenkinsLast = '2.235.5'
final jenkinsLts = '2.263.2'
buildPlugin(configurations: [
    [ platform: 'linux', jdk: '8', jenkins: null ],
    [ platform: 'linux', jdk: '8', jenkins: jenkinsLast, javaLevel: '8' ],
    [ platform: 'windows', jdk: '8', jenkins: jenkinsLast, javaLevel: '8' ],
    [ platform: 'linux', jdk: '11', jenkins: jenkinsLast, javaLevel: '8' ],
    [ platform: 'linux', jdk: '8', jenkins: jenkinsLts, javaLevel: '8' ],
    [ platform: 'windows', jdk: '8', jenkins: jenkinsLts, javaLevel: '8' ],
    [ platform: 'linux', jdk: '11', jenkins: jenkinsLts, javaLevel: '8' ],
])
